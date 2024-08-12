package cn.marci.raft.core.node;

import cn.marci.raft.common.Endpoint;
import cn.marci.raft.core.conf.RaftConf;
import cn.marci.raft.core.log.LogEntry;
import cn.marci.raft.core.log.LogId;
import cn.marci.raft.core.log.LogManager;
import cn.marci.raft.core.log.ReplicatorGroup;
import cn.marci.raft.core.node.impl.FSMCallerImpl;
import cn.marci.raft.core.node.impl.FileRaftMetaStorage;
import cn.marci.raft.core.rpc.RaftRpcFactory;
import cn.marci.raft.core.rpc.RpcService;
import cn.marci.raft.core.rpc.dto.*;
import cn.marci.raft.core.schedule.ElectTimer;
import cn.marci.raft.core.schedule.Timer;
import cn.marci.raft.rpc.RpcFactory;
import cn.marci.raft.rpc.RpcResponse;
import cn.marci.raft.utils.FutureUtils;
import cn.marci.raft.utils.NetUtils;
import cn.marci.raft.utils.ThreadPoolUtils;
import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.FatalExceptionHandler;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Slf4j
public class NodeImpl implements Node {

    private Endpoint id;

    private NodeId nodeId;

    private String group;

    private volatile Endpoint leaderId;

    private RoleEnum role = RoleEnum.FOLLOWER;

    private List<Endpoint> cluster = new CopyOnWriteArrayList<>();

    private volatile long term;

    private volatile Endpoint votedFor;

    private volatile long lastLeaderTimestamp;

    private VoteContext preVoteContext = new VoteContext(true);

    private VoteContext voteContext = new VoteContext(false);

    private final RaftConf conf;

    private ThreadPoolExecutor executor = new ThreadPoolExecutor(10, 20, 1, TimeUnit.MINUTES, new ArrayBlockingQueue<>(100), ThreadPoolUtils.getThreadFactory(false, "node-executor-"));

    private final RpcService rpcService;

    private Timer electTimer;

    private Timer voteTimeoutTimer;

    private ReadWriteLock lock = new ReentrantReadWriteLock();

    private Disruptor<ApplyTaskEvent> applyDisruptor;

    private LogManager logManager;

    private BallotBox ballotBox;

    private FSMCaller fsmCaller;

    private StateMachine sm;

    private ReplicatorGroup replicatorGroup;

    private RaftMetaStorage raftMetaStorage;

    public NodeImpl(String group, StateMachine stateMachine) {
        this.group = group;
        this.conf = RaftConf.getInstance();
        this.rpcService = RaftRpcFactory.getInstance().getRpcService();

        this.id = new Endpoint(NetUtils.getLocalIp(), conf.getRpcServerPort());
        this.nodeId = new NodeId(group, id);
        this.cluster.add(id);

        this.electTimer = new ElectTimer(this, conf.getElectionMinTimeout(), conf.getElectionMaxTimeout());
        this.voteTimeoutTimer = new Timer() {
            @Override
            protected void run() {
                lock.writeLock().lock();
                if (role != RoleEnum.CANDIDATE) {
                    lock.writeLock().unlock();
                    return;
                }
                log.warn("Node {} vote timeout for term={}, become follower", id, term);
                becomeFollower(term);
                preVote();
            }

            @Override
            protected long nextDelay() {
                return conf.getElectionMinTimeout();
            }

            @Override
            protected String timerName() {
                return "VoteTimeoutTimer";
            }

            @Override
            protected boolean isRepeatTask() {
                return false;
            }
        };

        this.applyDisruptor = new Disruptor<>(ApplyTaskEvent::new,
                1024,
                ThreadPoolUtils.getThreadFactory(false, "apply-disruptor-"),
                ProducerType.MULTI,
                new BlockingWaitStrategy());
        this.applyDisruptor.handleEventsWith(new ApplyTaskEventHandler());
        this.applyDisruptor.setDefaultExceptionHandler(new FatalExceptionHandler());

        this.sm = stateMachine;
        this.ballotBox = new BallotBox();
        this.logManager = new LogManager();
        this.fsmCaller = new FSMCallerImpl();
        this.raftMetaStorage = new FileRaftMetaStorage(String.format("./raft_meta/%s_%s.txt", group, id.toString().replaceAll("\\(|\\)", "")));
        this.replicatorGroup = new ReplicatorGroup(group, id, logManager, this, this.term, ballotBox, rpcService);
    }

    @Override
    public void start() {
        this.term = raftMetaStorage.getTerm();
        this.votedFor = raftMetaStorage.getVotedFor();

        CallbackQueue callbackQueue = new CallbackQueue();
        this.ballotBox.init(fsmCaller, callbackQueue);
        this.fsmCaller.init(callbackQueue, sm, logManager);
        this.logManager.init(String.format("./log_entry/%s/%s", group, id.toString().replaceAll("\\(|\\)", "")));

        this.applyDisruptor.start();
        becomeFollower(this.term);
        initClusterNodes();
    }

    @Override
    public void stop() {
        applyDisruptor.shutdown();
        executor.shutdown();
        removeCurrentNodeInCluster();
        RpcFactory.getInstance().stop();
    }

    private void initClusterNodes() {
        for (Endpoint endpoint : conf.getClusterNodes()) {
            if (endpoint.equals(id)) {
                continue;
            }
            if (!rpcService.connect(endpoint)) {
                log.warn("Node {} connect to {} failed", id, endpoint);
                continue;
            }
            addPeer(endpoint);
            rpcService.addPeer(endpoint, new ClusterRequest.AddPeerRequest(group, id, endpoint));
        }
        log.info("Node {} init cluster nodes {}", id, cluster.stream().map(Objects::toString).collect(Collectors.joining(",")));
    }

    private void removeCurrentNodeInCluster() {
        for (Endpoint endpoint : conf.getClusterNodes()) {
            if (endpoint.equals(id)) {
                continue;
            }
            if (!rpcService.connect(endpoint)) {
                log.warn("Node {} connect to {} failed", id, endpoint);
                continue;
            }
            rpcService.removePeer(endpoint, new ClusterRequest.RemovePeerRequest(group, id, endpoint));
        }
    }

    @Override
    public NodeId getNodeId() {
        return this.nodeId;
    }

    @Override
    public void addPeer(Endpoint endpoint) {
        this.lock.writeLock().lock();
        try {
            if (cluster.contains(endpoint)) {
                return;
            }
            log.info("Node {} add peer {}", id, endpoint);
            this.cluster.add(endpoint);
            if (role == RoleEnum.LEADER) {
                replicatorGroup.addReplicator(endpoint);
            }
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    @Override
    public void removePeer(Endpoint endpoint) {
        this.lock.writeLock().lock();
        try {
            log.info("Node {} remove peer {}", id, endpoint);
            this.cluster.remove(endpoint);
            if (role == RoleEnum.LEADER) {
                replicatorGroup.removeReplicator(endpoint);
            }
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    @Override
    public void handleElectTimeout() {
        boolean doUnlock = true;
        this.lock.writeLock().lock();
        try {
            if (role != RoleEnum.FOLLOWER) {
                return;
            }
            if (isCurrentLeaderValid()) {
                return;
            }
            leaderId = null;
            doUnlock = false;
            preVote();
        } finally {
            if (doUnlock) {
                this.lock.writeLock().unlock();
            }
        }
    }

    @Override
    public AppendEntriesResponse handleAppendEntries(AppendEntriesRequest appendEntries, Consumer<Object> sendRpcRespCallback) {
        boolean doUnlock = true;
        lock.writeLock().lock();
        try {
            if (log.isDebugEnabled()) {
                log.debug("Node {} receive AppendEntriesRequest from {}, request:{}", nodeId, appendEntries.getLeaderId(), appendEntries);
            }
            if (appendEntries.getTerm() < this.term) {
                log.warn("Node {} ignore AppendEntriesRequest from {}, term={}, currentTerm={}", nodeId, appendEntries.getLeaderId(), appendEntries.getTerm(), term);
                return AppendEntriesResponse.builder()
                        .term(term)
                        .success(false)
                        .build();
            }
            checkTerm(appendEntries.getTerm(), appendEntries.getLeaderId());
            if (!Objects.equals(leaderId, appendEntries.getLeaderId())) {
                //集群有两个leader，自身降为follower 并且term加1重新选择leader
                log.error("Node {} ignore AppendEntriesRequest from {} because cluster has more leader, term={}, currentTerm={}, current leader:{}", nodeId, appendEntries.getLeaderId(), appendEntries.getTerm(), term, leaderId);
                becomeFollower(appendEntries.getTerm() + 1);
                return AppendEntriesResponse.builder()
                        .term(appendEntries.getTerm() + 1)
                        .success(false)
                        .build();
            }

            lastLeaderTimestamp = System.currentTimeMillis();
            long localLastTerm = logManager.getTerm(appendEntries.getPrevLogIndex());
            if (appendEntries.getPrevLogTerm() != localLastTerm) {
                long lastLogIndex = logManager.getLastLogIndex();
                log.warn("Node {} ignore AppendEntriesRequest from {}, term={}, currentTerm={}, request last term={} and index={}, local term={} index={},current leader:{}",
                        nodeId, appendEntries.getLeaderId(), appendEntries.getTerm(), term, appendEntries.getPrevLogTerm(), appendEntries.getPrevLogIndex(), localLastTerm, lastLogIndex, leaderId);
                return AppendEntriesResponse.builder()
                        .success(false)
                        .term(this.term)
                        .lastLogIndex(lastLogIndex)
                        .build();
            }

            //心跳
            if (appendEntries.getEntries() == null || appendEntries.getEntries().isEmpty()) {
                AppendEntriesResponse response = AppendEntriesResponse.builder()
                        .term(term)
                        .success(true)
                        .lastLogIndex(logManager.getLastLogIndex())
                        .build();
                doUnlock = false;
                this.lock.writeLock().unlock();
                ballotBox.setLastCommittedIndex(Math.min(appendEntries.getLastCommittedIndex(), appendEntries.getPrevLogIndex()));
                return response;
            }
            FollowerStableCallback followerStableCallback = new FollowerStableCallback(appendEntries, this.term, AppendEntriesResponse.builder().term(this.term), sendRpcRespCallback);
            this.logManager.appendEntries(appendEntries.getEntries().stream()
                    .map(EntryMeta::toLogEntry)
                    .collect(Collectors.toList()), followerStableCallback);
            return null;
        } finally {
            if (doUnlock) {
                lock.writeLock().unlock();
            }
        }
    }

    @Override
    public RequestVoteResponse handleVote(RequestVoteRequest requestVoteRequest) {
        boolean doUnlock = true;
        this.lock.writeLock().lock();
        try {
            do {
                if (requestVoteRequest.getTerm() >= this.term) {
                    log.info("Node {} received RequestVoteRequest from {}, term={}, currentTerm={}", nodeId, requestVoteRequest.getCandidateId(), requestVoteRequest.getTerm(), term);
                    if (requestVoteRequest.getTerm() > this.term) {
                        //降级且更新term
                        becomeFollower(requestVoteRequest.getTerm());
                    }
                } else {
                    log.info("Node {} ignore RequestVoteRequest from {}, term={}, currentTerm={}", nodeId, requestVoteRequest.getCandidateId(), requestVoteRequest.getTerm(), term);
                    break;
                }
                doUnlock = false;
                this.lock.writeLock().unlock();

                LogId lastLogId = logManager.getLastLogId();

                doUnlock = true;
                this.lock.writeLock().lock();
                if (requestVoteRequest.getTerm() != this.term) {
                    break;
                }
                boolean logIsOk = new LogId(requestVoteRequest.getLastLogTerm(), requestVoteRequest.getLastLogIndex()).compareTo(lastLogId) >= 0;
                if (logIsOk && votedFor == null) {
                    this.votedFor = requestVoteRequest.getCandidateId();
                    this.raftMetaStorage.setVotedFor(requestVoteRequest.getCandidateId());
                }
            } while (false);
            return RequestVoteResponse.builder()
                    .term(term)
                    .voteGranted(requestVoteRequest.getTerm() == this.term && requestVoteRequest.getCandidateId().equals(votedFor))
                    .from(id)
                    .build();
        } finally {
            if (doUnlock) {
                this.lock.writeLock().unlock();
            }
        }
    }

    @Override
    public RequestVoteResponse handlePreVote(RequestVoteRequest requestVoteRequest) {
        boolean doUnlock = true;
        this.lock.writeLock().lock();
        try {
            boolean granted = false;
            do {
                if (leaderId != null && isCurrentLeaderValid()) {
                    log.info("Node {} ignore PreVoteRequest from {}, term={}, currentTerm={}, current leader:{}", nodeId, requestVoteRequest.getCandidateId(), requestVoteRequest.getTerm(), term, leaderId);
                    break;
                }
                if (requestVoteRequest.getTerm() < term) {
                    log.info("Node {} ignore PreVoteRequest from {}, term={}, currentTerm={}", nodeId, requestVoteRequest.getCandidateId(), requestVoteRequest.getTerm(), term);
                    break;
                }
                doUnlock = false;
                this.lock.writeLock().unlock();

                LogId lastLogId = logManager.getLastLogId();

                doUnlock = true;
                this.lock.writeLock().lock();
                LogId requestId = new LogId(requestVoteRequest.getLastLogTerm(), requestVoteRequest.getLastLogIndex());
                granted = requestId.compareTo(lastLogId) >= 0;
                log.info("Node {} received PreVoteRequest from {}, term={}, currentTerm={}", nodeId, requestVoteRequest.getCandidateId(), requestVoteRequest.getTerm(), term);
            } while (false);
            return RequestVoteResponse.builder()
                    .term(term)
                    .voteGranted(granted)
                    .from(id)
                    .build();
        } finally {
            if (doUnlock) {
                this.lock.writeLock().unlock();
            }
        }
    }


    private void preVote() {
        long oldTerm;
        try {
            log.info("Node {} start preVote, term={}", id, term);
            oldTerm = term;
        } finally {
            lock.writeLock().unlock();
        }

        LogId lastLogId = logManager.getLastLogId();

        boolean doUnlock = true;
        lock.writeLock().lock();
        try {
            if (oldTerm != term) {
                log.warn("Node {} preVote failed, term changed, oldTerm={}, currentTerm={}", id, oldTerm, term);
                return;
            }

            preVoteContext.init(term + 1, cluster);

            for (Endpoint endpoint : cluster) {
                if (endpoint.equals(id)) {
                    continue;
                }
                if (!rpcService.connect(endpoint)) {
                    log.warn("Node {} connect to {} failed", id, endpoint);
                    continue;
                }
                RequestVoteRequest request = RequestVoteRequest.builder()
                        .preVote(true)
                        .group(group)
                        .term(term + 1)
                        .candidateId(id)
                        .lastLogTerm(lastLogId.getTerm())
                        .lastLogIndex(lastLogId.getIndex())
                        .toEndpoint(endpoint)
                        .build();
                CompletableFuture<Void> cf = rpcService.requestVote(endpoint, request)
                        .thenAcceptAsync(resp -> handlePreVoteResponse(resp, term), executor);
                FutureUtils.addHandleExceptionStage(cf, log);
            }
            preVoteContext.grant(id);
            if (preVoteContext.isGranted()) {
                doUnlock = false;
                electSelf();
            }
        } finally {
            if (doUnlock) {
                lock.writeLock().unlock();
            }
        }
    }

    private void handlePreVoteResponse(RequestVoteResponse resp, long term) {
        boolean doUnlock = true;
        this.lock.writeLock().lock();
        try {
            if (role != RoleEnum.FOLLOWER) {
                log.warn("Node {} ignore preVoteResponse from {}, current role: {}", nodeId, resp.getFrom(), role);
                return;
            }
            if (this.term != term) {
                log.warn("Node {} ignore preVoteResponse from {}, current term: {}, preVote term: {}", nodeId, resp.getFrom(), this.term, term);
                return;
            }
            if (resp.getTerm() > this.term) {
                log.warn("Node {} ignore preVoteResponse from {}, term: {}, except term: {}", nodeId, resp.getFrom(), resp.getTerm(), this.term);
                becomeFollower(resp.getTerm());
                return;
            }
            log.info("Node {} receive preVoteResponse from {}, term:{}, voteGranted: {}", nodeId, resp.getFrom(), this.term, resp.isVoteGranted());
            if (resp.isVoteGranted()) {
                this.preVoteContext.grant(resp.getFrom());
                if (this.preVoteContext.isGranted()) {
                    doUnlock = false;
                    electSelf();
                }
            }
        } finally {
            if (doUnlock) {
                this.lock.writeLock().unlock();
            }
        }
    }

    private void handleVoteResponse(RequestVoteResponse resp, long term) {
        this.lock.writeLock().lock();
        try {
            if (role != RoleEnum.CANDIDATE) {
                log.warn("Node {} ignore voteResponse from {}, current role: {}", nodeId, resp.getFrom(), role);
                return;
            }
            if (this.term != term) {
                log.warn("Node {} ignore VoteResponse from {}, current term: {}, vote term: {}", nodeId, resp.getFrom(), this.term, term);
                return;
            }
            if (resp.getTerm() > this.term) {
                log.warn("Node {} ignore VoteResponse from {}, term: {}, except term: {}", nodeId, resp.getFrom(), resp.getTerm(), this.term);
                becomeFollower(resp.getTerm());
                return;
            }
            if (resp.isVoteGranted()) {
                this.voteContext.grant(resp.getFrom());
                if (this.voteContext.isGranted()) {
                    becomeLeader();
                }
            }
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    private void electSelf() {
        long oldTerm;
        try {
            log.info("Node {} start to elect self, term:{}", nodeId, term);
            if (role == RoleEnum.FOLLOWER) {
                this.electTimer.stop();
            }
            role = RoleEnum.CANDIDATE;
            leaderId = null;
            term++;
            votedFor = id;
            voteTimeoutTimer.start();
            voteContext.init(term, cluster);
            oldTerm = this.term;
        } finally {
            lock.writeLock().unlock();
        }

        LogId lastLogId = this.logManager.getLastLogId();

        lock.writeLock().lock();
        try {
            if (oldTerm != this.term) {
                return;
            }

            for (Endpoint endpoint : cluster) {
                if (endpoint.equals(id)) {
                    continue;
                }
                if (!rpcService.connect(endpoint)) {
                    log.warn("Node {} connect to {} failed", id, endpoint);
                    continue;
                }
                RequestVoteRequest request = RequestVoteRequest.builder()
                        .preVote(false)
                        .group(group)
                        .term(term)
                        .candidateId(id)
                        .lastLogTerm(lastLogId.getTerm())
                        .lastLogIndex(lastLogId.getIndex())
                        .toEndpoint(endpoint)
                        .build();
                CompletableFuture<Void> cf = rpcService.requestVote(endpoint, request)
                        .thenAcceptAsync(resp -> handleVoteResponse(resp, term), executor);
                FutureUtils.addHandleExceptionStage(cf, log);
            }
            raftMetaStorage.setTermAndVotedFor(this.term, this.id);
            this.voteContext.grant(id);
            if (this.voteContext.isGranted()) {
                becomeLeader();
            }
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    private void checkTerm(long requestTerm, Endpoint from) {
        if (requestTerm > this.term) {
            log.warn("Node {} receive request from new leader {}, term: {}, current term: {}", nodeId, from, requestTerm, this.term);
            becomeFollower(requestTerm);
        } else if (this.role == RoleEnum.CANDIDATE) {
            log.warn("Candidate Node {} receive request from {}, term: {}", nodeId, from, requestTerm);
            becomeFollower(requestTerm);
        } else if (leaderId == null) {
            log.warn("Follower Node {} receive request from {}, term: {}", nodeId, from, requestTerm);
            becomeFollower(requestTerm);
        }
        leaderId = from;
    }

    //方法在writeLock中
    private void becomeFollower(long term) {
        if (role == RoleEnum.CANDIDATE) {
            voteTimeoutTimer.stop();
        } else if (role == RoleEnum.LEADER) {
            this.ballotBox.clearPendingTasks();
        }

        this.leaderId = null;
        this.role = RoleEnum.FOLLOWER;

        if (term > this.term) {
            this.term = term;
            this.votedFor = null;
            raftMetaStorage.setTermAndVotedFor(this.term, this.votedFor);
        }
        replicatorGroup.stopAll();
        electTimer.reset();
        fsmCaller.onLeaderStop();
        log.info("Node {} become follower, term: {}, current term: {}", id, term, this.term);
    }

    private void becomeLeader() {
        if (this.role != RoleEnum.CANDIDATE) {
            log.error("Node {} become leader failed, role: {}", id, role);
            return;
        }

        this.role = RoleEnum.LEADER;
        this.leaderId = id;
        this.replicatorGroup.resetTerm(this.term);
        for (Endpoint endpoint : this.cluster) {
            if (id.equals(endpoint)) {
                continue;
            }
            if (!replicatorGroup.addReplicator(endpoint)) {
                log.error("add replicator {} failed", endpoint);
            }
        }
        this.ballotBox.resetPendingIndex(this.logManager.getLastLogIndex() + 1);

        this.electTimer.stop();
        fsmCaller.onLeaderStart(this.term);
        log.info("Node {} become leader, term: {}", id, term);
    }

    private boolean isCurrentLeaderValid() {
        return System.currentTimeMillis() - this.lastLeaderTimestamp < this.conf.getElectionMinTimeout();
    }

    @Override
    public void apply(Task task) {
        if (task == null || task.getDoneCf() == null) {
            throw new IllegalArgumentException("task is null or done CompletableFuture is null");
        }
        LogEntry logEntry = new LogEntry();
        logEntry.setData(task.getData());

        applyDisruptor.getRingBuffer().publishEvent((event, sequence) -> {
            event.reset();
            event.setExceptedTerm(task.getExceptedTerm());
            event.setLogEntry(logEntry);
            event.setDoneCf(task.getDoneCf());
        });
        if (log.isDebugEnabled()) {
            log.debug("Node {} published apply task", id);
        }
    }

    @Data
    private static class ApplyTaskEvent {
        private long exceptedTerm;
        private CompletableFuture<?> doneCf;
        private LogEntry logEntry;

        public void reset() {
            this.exceptedTerm = -1;
            this.doneCf = null;
            this.logEntry = null;
        }
    }

    private class ApplyTaskEventHandler implements EventHandler<ApplyTaskEvent> {

        private List<ApplyTaskEvent> list = new ArrayList<>(NodeImpl.this.conf.getBatchSize());

        @Override
        public void onEvent(ApplyTaskEvent event, long sequence, boolean endOfBatch) throws Exception {
            list.add(event);

            if (list.size() >= NodeImpl.this.conf.getBatchSize() || endOfBatch) {
                executeApplyEvent(list);
                list.forEach(ApplyTaskEvent::reset);
                list.clear();
            }
        }
    }

    private void executeApplyEvent(List<ApplyTaskEvent> tasks) {
        this.lock.writeLock().lock();
        try {
            if (role != RoleEnum.LEADER) {
                log.error("Node {} execute apply event failed, because this node not leader, role: {}", id, role);
                tasks.forEach(event -> event.doneCf.completeExceptionally(new IllegalStateException("current node is not leader")));
                return;
            }
            List<LogEntry> list = new ArrayList<>(tasks.size());
            for (ApplyTaskEvent task : tasks) {
                if (task.exceptedTerm != -1 && task.exceptedTerm != term) {
                    log.error("Node {} execute apply event failed, because except term not current term, exceptedTerm: {}, currentTerm: {}", id, task.exceptedTerm, term);
                    task.doneCf.completeExceptionally(new IllegalStateException("term is not match, exceptedTerm: " + task.exceptedTerm + ", currentTerm: " + term));
                    task.reset();
                    continue;
                }
                ballotBox.appendPendingTask(cluster, task.doneCf);

                task.logEntry.getId().setTerm(term);
                list.add(task.logEntry);
                task.reset();
            }
            logManager.appendEntries(list, new LeaderStableCallback(list));
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    class LeaderStableCallback extends LogManager.StableCallback {

        public LeaderStableCallback(List<LogEntry> entries) {
            super(entries);
        }

        @Override
        public void run(boolean success) {
            int cnt = entries != null ? entries.size() : 0;
            if (success) {
                NodeImpl.this.ballotBox.commitAt(this.firstLogIndex, this.firstLogIndex + cnt - 1, NodeImpl.this.id);
            } else {
                log.error("Node{} append [{}, {}] entries failed", NodeImpl.this.id, this.firstLogIndex, this.firstLogIndex + cnt - 1);
            }
        }
    }

    class FollowerStableCallback extends LogManager.StableCallback {
        private long committedIndex;
        private long term;
        private AppendEntriesResponse.AppendEntriesResponseBuilder responseBuilder;
        private Consumer<Object> sendRpcRespCallback;

        public FollowerStableCallback(AppendEntriesRequest request, long term, AppendEntriesResponse.AppendEntriesResponseBuilder responseBuilder, Consumer<Object> sendRpcRespCallback) {
            super(null);
            this.committedIndex = Math.min(
                    request.getLastCommittedIndex(),
                    request.getPrevLogIndex() + request.getEntries().size());
            this.term = term;
            this.responseBuilder = responseBuilder;
            this.sendRpcRespCallback = sendRpcRespCallback;
        }

        @Override
        protected void run(boolean success) {
            if (!success) {
                sendRpcRespCallback.accept(new RpcResponse(null, false, null, "append entries failed", null));
                return;
            }
            NodeImpl.this.lock.readLock().lock();
            try {
                if (this.term != NodeImpl.this.term) {
                    this.responseBuilder.success(false)
                            .term(NodeImpl.this.term);
                    sendRpcRespCallback.accept(this.responseBuilder.build());
                    return;
                }
            } finally {
                NodeImpl.this.lock.readLock().unlock();
            }
            this.responseBuilder.success(true)
                    .term(this.term);
            NodeImpl.this.ballotBox.setLastCommittedIndex(this.committedIndex);
            sendRpcRespCallback.accept(this.responseBuilder.build());
        }
    }

    @Override
    public void increaseTermTo(long newTerm) {
        this.lock.writeLock().lock();
        try {
            if (newTerm < this.term) {
                return;
            }
            becomeFollower(newTerm);
        } finally {
            this.lock.writeLock().unlock();
        }
    }
}
