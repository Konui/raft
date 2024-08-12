package cn.marci.raft.core.log;

import cn.marci.raft.common.Endpoint;
import cn.marci.raft.common.Lifecycle;
import cn.marci.raft.core.conf.RaftConf;
import cn.marci.raft.core.node.ErrorCode;
import cn.marci.raft.core.rpc.RpcService;
import cn.marci.raft.core.rpc.dto.AppendEntriesRequest;
import cn.marci.raft.core.rpc.dto.AppendEntriesResponse;
import cn.marci.raft.core.rpc.dto.EntryMeta;
import cn.marci.raft.utils.FutureUtils;
import cn.marci.raft.utils.ThreadPoolUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

@Slf4j
public class Replicator implements Lifecycle {
    private static final int BACKLOG = 256;
    private final Endpoint endpoint;

    private volatile long nextIndex;

    ScheduledFuture<?> heartbeatFuture;

    /**
     * 最近一次rpc请求
     */
    private Inflight rpcInFly;

    /**
     * 请求队列
     */
    private final ArrayDeque<Inflight> inflights = new ArrayDeque<>();

    /**
     * 请求序列
     */
    private int reqSeq = 0;

    /**
     * 期望的响应序列
     */
    private int requiredNextSeq = 0;

    /**
     * 状态版本，当重置 inflight 请求队列时会递增，以实现忽略版本不匹配的 inflight 请求响应
     */
    private int version = 0;

    /**
     * 记录已经收到但是还没有被处理的响应，按照请求序列从小到大排序，
     * 响应的顺序是未知的，但是需要保证处理的顺序
     */
    private final PriorityQueue<RpcResponse> pendingResponses = new PriorityQueue<>(50);

    private ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private volatile State state;

    private ReplicatorContext context;

    private RpcService rpcService;

    private ScheduledFuture<?> blockTimer;

    private volatile long waitId = -1;

    public enum State {
        Created,
        Probe, // probe follower state
        Snapshot, // installing snapshot to follower
        Replicate, // replicate logs normally
        Destroyed // destroyed
    }

    public Replicator(Endpoint endpoint, ReplicatorContext context) {
        this.endpoint = endpoint;
        this.context = context;
        this.nextIndex = context.getLogManager().getLastLogIndex() + 1;
        this.state = State.Created;
        this.rpcService = context.getRpcService();
    }


    public static Replicator newInstanceAndStart(Endpoint endpoint, ReplicatorContext context) {
        Replicator r = new Replicator(endpoint, context);
        r.start();
        return r;
    }

    @Override
    public void start() {
        startHeartbeatTimer();
        sendProbe();
    }

    @Override
    public void stop() {
        this.lock.writeLock().lock();
        try {
            this.state = State.Destroyed;
            stopHeartbeatTimer();
            if (waitId > 0) {
                context.getLogManager().removeWaiter(this.waitId);
            }
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    // In-flight request type
    enum RequestType {
        Snapshot, // install snapshot
        AppendEntries // replicate logs
    }

    static class Inflight {
        // In-flight request count
        final int count;
        // Start log index
        final long startIndex;
        // RPC future
        final Future rpcFuture;
        final RequestType requestType;
        // Request sequence.
        final int seq;

        public Inflight(final RequestType requestType, final long startIndex, final int count,
                        final int seq, final Future rpcFuture) {
            super();
            this.seq = seq;
            this.requestType = requestType;
            this.count = count;
            this.startIndex = startIndex;
            this.rpcFuture = rpcFuture;
        }

        boolean isSendingLogEntries() {
            return this.requestType == RequestType.AppendEntries && this.count > 0;
        }
    }

    private void addInflight(final RequestType reqType,
                             final long startIndex,
                             final int count,
                             final int seq,
                             final Future rpcInfly) {
        // 更新本地记录的最近一次发送的 inflight RPC 请求
        this.rpcInFly = new Inflight(reqType, startIndex, count, seq, rpcInfly);
        // 标记当前请求为 inflight
        this.inflights.add(this.rpcInFly);
    }

    private void onRpcReturn(final RequestType reqType,
                             final AppendEntriesRequest request,
                             final AppendEntriesResponse response,
                             final int seq,
                             final int stateVersion) {
        this.lock.writeLock().lock();
        try {
            if (this.version != stateVersion) {
                return;
            }
            this.pendingResponses.add(new RpcResponse(request, response, seq, reqType));
            if (this.pendingResponses.size() > BACKLOG) {
                log.error("Pending responses size {} exceeds the limit {}", this.pendingResponses.size(), BACKLOG);
                resetInflights();
                this.state = State.Probe;
                this.sendProbe();
                return;
            }

            // 标识是否继续发送 AppendEntries 请求
            boolean continueSendEntries = false;
            try {
                int processed = 0;
                while (!pendingResponses.isEmpty()) {
                    final RpcResponse queuedPipelinedResponse = pendingResponses.peek();
                    if (queuedPipelinedResponse.seq != requiredNextSeq) {
                        if (processed > 0) {
                            break;
                        } else {
                            continueSendEntries = false;
                            return;
                        }
                    }
                    pendingResponses.remove();
                    processed++;
                    Inflight inflight = inflights.poll();
                    if (inflight == null) {
                        continue;
                    }
                    if (inflight.seq != queuedPipelinedResponse.seq) {
                        log.warn("Expected seq {} but got {}", inflight.seq, queuedPipelinedResponse.seq);
                        resetInflights();
                        this.state = State.Probe;
                        continueSendEntries = false;
                        return;
                    }
                    try {
                        switch (queuedPipelinedResponse.requestType) {
                            case AppendEntries:
                                continueSendEntries = onAppendEntriesReturned(inflight,
                                        (AppendEntriesRequest) queuedPipelinedResponse.request,
                                        (AppendEntriesResponse) queuedPipelinedResponse.response);
                                break;
//                            case Snapshot:
//                                continueSendEntries = onInstallSnapshotReturned(id, r, queuedPipelinedResponse.status,
//                                        (InstallSnapshotRequest) queuedPipelinedResponse.request,
//                                        (InstallSnapshotResponse) queuedPipelinedResponse.response);
//                                break;
                        }
                    } finally {
                        if (continueSendEntries) {
                            getAndIncrementRequiredNextSeq();
                        } else {
                            break;
                        }
                    }
                }
            } finally {
                if (continueSendEntries) {
                    sendEntries();
                }
            }
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    private int getAndIncrementRequiredNextSeq() {
        final int prev = this.requiredNextSeq;
        this.requiredNextSeq++;
        if (this.requiredNextSeq < 0) {
            this.requiredNextSeq = 0;
        }
        return prev;
    }

    private void resetInflights() {
        this.version++;
        this.inflights.clear();
        this.pendingResponses.clear();
        final int rs = Math.max(this.reqSeq, this.requiredNextSeq);
        this.reqSeq = this.requiredNextSeq = rs;
//        releaseReader();
    }

    long getNextSendIndex() {
        if (this.inflights.isEmpty()) {
            return this.nextIndex;
        }
        if (this.rpcInFly != null && this.rpcInFly.isSendingLogEntries()) {
            return this.rpcInFly.startIndex + this.rpcInFly.count;
        }
        return -1L;
    }

    private int getAndIncrementReqSeq() {
        final int prev = this.reqSeq;
        this.reqSeq++;
        if (this.reqSeq < 0) {
            this.reqSeq = 0;
        }
        return prev;
    }

    public void startHeartbeatTimer() {
        ScheduledThreadPoolExecutor scheduledPool = ThreadPoolUtils.getPublicScheduledPool();
        heartbeatFuture = scheduledPool.scheduleAtFixedRate(this::sendHeartbeat, 0, RaftConf.getInstance().getHeartbeatInterval(), TimeUnit.MILLISECONDS);
    }

    public void stopHeartbeatTimer() {
        if (heartbeatFuture != null) {
            heartbeatFuture.cancel(true);
        }
    }

    public void sendHeartbeat() {
        sendEmptyEntries(true);
    }

    public void sendProbe() {
        sendEmptyEntries(false);
    }

    private void sendEntries() {
        this.lock.writeLock().lock();
        try {
            long prevSendIndex = -1;
            while (true) {
                final long nextSendingIndex = getNextSendIndex();
                if (nextSendingIndex > prevSendIndex) {
                    if (sendEntries(nextSendingIndex)) {
                        prevSendIndex = nextSendingIndex;
                    } else {
                        break;
                    }
                } else {
                    break;
                }
            }
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    private boolean sendEntries(long nextSendingIndex) {
        AppendEntriesRequest.AppendEntriesRequestBuilder builder = AppendEntriesRequest.builder();
        if (!fillCommonFields(builder, nextSendingIndex - 1, false)) {
//            //TODO install snapshot
//            return false;
        }
        int batchSize = RaftConf.getInstance().getBatchSize();
        List<LogEntry> list = new ArrayList<>();
        for (int i = 0; i<batchSize; i++) {
            LogEntry logEntry = context.getLogManager().getLogEntry(nextSendingIndex + i);
            if (logEntry == null) {
                break;
            }
            list.add(logEntry);
        }
        if (list.isEmpty()) {
            if (log.isDebugEnabled()) {
                log.debug("Replicator has no entries to send to {}", endpoint);
            }
            if (nextSendingIndex < context.getLogManager().getFirstLogIndex()) {
                //TODO install snapshot
                return false;
            }
            waitMoreEntries(nextSendingIndex);
            return false;
        }
        builder.entries(list.stream()
                .map(EntryMeta::toMeta)
                .collect(Collectors.toList()));
        AppendEntriesRequest request = builder.build();
        final int v = this.version;
        final int seq = getAndIncrementReqSeq();
        CompletableFuture<Void> future = rpcService.appendEntries(endpoint, request)
                .thenAcceptAsync(resp -> onRpcReturn(RequestType.AppendEntries, request, resp, seq, v));
        addInflight(RequestType.AppendEntries, nextSendingIndex, list.size(), seq, future);
        if (log.isDebugEnabled()) {
            log.debug("Replicator send entries to {} with request {}", endpoint, request);
        }
        return true;
    }

    private void waitMoreEntries(long nextSendingIndex) {
        this.lock.writeLock().lock();
        try {
            if (waitId >= 0) {
                return;
            }
            this.waitId = context.getLogManager().wait(nextSendingIndex - 1, () -> {
                this.lock.writeLock().lock();
                try {
                    this.waitId = -1;
                    sendEntries();
                } finally {
                    this.lock.writeLock().unlock();
                }
            });
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    private void sendEmptyEntries(boolean isHeartbeat) {
        AppendEntriesRequest.AppendEntriesRequestBuilder builder = AppendEntriesRequest.builder();
        if (!fillCommonFields(builder, this.nextIndex - 1, isHeartbeat)) {
//            //TODO install snapshot
//            return;
        }
        if (isHeartbeat) {
            AppendEntriesRequest request = builder.build();
            CompletableFuture<Void> future = rpcService.appendEntries(endpoint, request)
                    .thenAcceptAsync(resp -> onHeartbeatReturned(request, resp));
            FutureUtils.addHandleExceptionStage(future, log);
            return;
        }
        this.lock.writeLock().lock();
        try {
            builder.entries(Collections.emptyList());
            AppendEntriesRequest request = builder.build();
            this.state = State.Probe;
            final int stateVersion = this.version;
            final int seq = getAndIncrementReqSeq();
            CompletableFuture<Void> future = rpcService.appendEntries(endpoint, request)
                    .thenAcceptAsync(resp -> onRpcReturn(RequestType.AppendEntries, request, resp, seq, stateVersion));
            addInflight(RequestType.AppendEntries, this.nextIndex, 0, seq, future);
            if (log.isDebugEnabled()) {
                log.debug("Replicator send probe request to {}", endpoint);
            }
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    private boolean fillCommonFields(AppendEntriesRequest.AppendEntriesRequestBuilder rb, long prevLogIndex, final boolean isHeartbeat) {
        long prevLogTerm = context.getLogManager().getTerm(prevLogIndex);
        if (prevLogTerm == 0 && prevLogIndex != 0) {
            if (!isHeartbeat) {
                long firstLogIndex = context.getLogManager().getFirstLogIndex();
                if (prevLogIndex < firstLogIndex) {
                    throw new IllegalArgumentException("prevLogIndex:" + prevLogIndex + " is less than firstLogIndex:" + firstLogIndex);
                }
                return false;
            } else {
                prevLogIndex = 0;
            }
        }
        rb.group(context.getGroupId())
                .term(context.getTerm())
                .prevLogTerm(prevLogTerm)
                .prevLogIndex(prevLogIndex)
                .lastCommittedIndex(context.getBallotBox().getLastCommittedIndex())
                .toEndpoint(endpoint)
                .leaderId(context.getLeaderEndpoint());
        return true;
    }

    private void onHeartbeatReturned(AppendEntriesRequest request, AppendEntriesResponse response) {
        this.lock.writeLock().lock();
        try {
            if (request.getTerm() < response.getTerm()) {
                stop();
                context.getNode().increaseTermTo(response.getTerm());
                return;
            }
            if (!response.isSuccess() && response.getLastLogIndex() == 0) {
                sendProbe();
            }
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    private boolean onAppendEntriesReturned(final Inflight inflight,
                                            final AppendEntriesRequest request,
                                            final AppendEntriesResponse response) {


        this.lock.writeLock().lock();
        try {
            if (log.isDebugEnabled()) {
                log.debug("Replicator receive response, request: {}, response: {}", request, response);
            }
            if (inflight.startIndex != request.getPrevLogIndex() + 1) {
                log.warn("Replicator receive out of order response, expect startIndex: {}, but actual: {}", inflight.startIndex, request.getPrevLogIndex());
                resetInflights();
                this.state = State.Probe;
                sendProbe();
                return false;
            }
            if (inflight.rpcFuture.state() == Future.State.FAILED) {
                log.error("AppendEntriesRequest failed", FutureUtils.extractRealException(inflight.rpcFuture.exceptionNow()));
                resetInflights();
                this.state = State.Probe;
                block();
                return false;
            }
            if (!response.isSuccess()) {
                if (log.isDebugEnabled()) {
                    log.error("Replicator receive failed response, request: {}, response: {}", request, response);
                }
                if (response.getErrorCode() == ErrorCode.BUSY.getErrorCode()) {
                    resetInflights();
                    this.state = State.Probe;
                    block();
                    return false;
                }
                if (response.getTerm() > request.getTerm()) {
                    stop();
                    context.getNode().increaseTermTo(response.getTerm());
                    return false;
                }
                resetInflights();
                if (response.getLastLogIndex() + 1 < this.nextIndex) {
                    this.nextIndex = response.getLastLogIndex() + 1;
                } else {
                    if (this.nextIndex > 1) {
                        this.nextIndex--;
                    }
                }
                sendProbe();
                return false;
            }
            if (request.getTerm() != response.getTerm()) {
                log.error("Replicator receive failed response, because term not match, request: {}, response: {}", request, response);
                resetInflights();
                this.state = State.Probe;
                return false;
            }
            final int entriesSize = request.getEntries().size();
            if (entriesSize > 0) {
                context.getBallotBox().commitAt(this.nextIndex, this.nextIndex + entriesSize - 1, endpoint);
            }
            this.state = State.Replicate;
            this.nextIndex += entriesSize;
            if (log.isDebugEnabled()) {
                log.debug("Replicator will send next append entries, nextIndex: {}", this.nextIndex);
            }
            return true;
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    private static class RpcResponse implements Comparable<RpcResponse> {
        final AppendEntriesRequest request;
        final AppendEntriesResponse response;
        final int seq;
        final RequestType requestType;

        public RpcResponse(AppendEntriesRequest request, AppendEntriesResponse response, int seq, RequestType requestType) {
            this.request = request;
            this.response = response;
            this.seq = seq;
            this.requestType = requestType;
        }

        /**
         * Sort by sequence.
         */
        @Override
        public int compareTo(final RpcResponse o) {
            return Integer.compare(this.seq, o.seq);
        }
    }

    private void block() {
        this.lock.writeLock().lock();
        try {
            if (this.blockTimer != null) {
                return;
            }
            ScheduledThreadPoolExecutor scheduledPool = ThreadPoolUtils.getPublicScheduledPool();
            this.blockTimer = scheduledPool.schedule(() -> {
                this.lock.writeLock().lock();
                try {
                    this.blockTimer = null;
                    sendProbe();
                } finally {
                    this.lock.writeLock().unlock();
                }
            }, RaftConf.getInstance().getHeartbeatInterval(), TimeUnit.MILLISECONDS);

        } finally {
            this.lock.writeLock().unlock();
        }
    }
}
