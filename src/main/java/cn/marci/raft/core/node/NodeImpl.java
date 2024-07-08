package cn.marci.raft.core.node;

import cn.marci.raft.common.Endpoint;
import cn.marci.raft.core.conf.RaftConf;
import cn.marci.raft.core.rpc.RaftRpcFactory;
import cn.marci.raft.core.rpc.RpcService;
import cn.marci.raft.core.rpc.dto.AppendEntriesRequest;
import cn.marci.raft.core.rpc.dto.AppendEntriesResponse;
import cn.marci.raft.core.rpc.dto.RequestVoteRequest;
import cn.marci.raft.core.rpc.dto.RequestVoteResponse;
import cn.marci.raft.core.schedule.ElectTimer;
import cn.marci.raft.core.schedule.SendHeartbeatTimer;
import cn.marci.raft.core.schedule.Timer;
import cn.marci.raft.utils.FutureUtils;
import cn.marci.raft.utils.NetUtils;
import cn.marci.raft.utils.ThreadPoolUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

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

    private VoteContext preVoteContext = new VoteContext(true);

    private VoteContext voteContext = new VoteContext(false);

    private final RaftConf conf;

    private ThreadPoolExecutor executor = new ThreadPoolExecutor(10, 20, 1, TimeUnit.MINUTES, new ArrayBlockingQueue<>(100), ThreadPoolUtils.getThreadFactory(false, "node-executor-"));

    private final RpcService rpcService;

    private Timer electTimer;

    private Timer sendHeartbeatTimer;

    private Timer voteTimeoutTimer;

    private ReadWriteLock lock = new ReentrantReadWriteLock();

    public NodeImpl() {
        this(null);
    }

    public NodeImpl(String group) {
        this.group = group;
        this.conf = RaftConf.getInstance();
        this.rpcService = RaftRpcFactory.getInstance().getRpcService();
        this.cluster.addAll(conf.getClusterNodes());
        if (cluster.isEmpty()) {
            throw new IllegalArgumentException("raft.nodes is null or empty");
        }
        this.id = new Endpoint(NetUtils.getLocalIp(), conf.getRpcServerPort());
        this.nodeId = new NodeId(group, id);
        this.electTimer = new ElectTimer(this, conf.getElectionMinTimeout(), conf.getElectionMaxTimeout());
        this.sendHeartbeatTimer = new SendHeartbeatTimer(this, conf.getHeartbeatInterval());
        this.voteTimeoutTimer = new Timer() {
            @Override
            protected void run() {
                lock.writeLock().lock();
                if (role != RoleEnum.CANDIDATE) {
                    lock.writeLock().unlock();
                    return;
                }
                log.warn("Node {} vote timeout for term={}, become follower", id, term);
                becomeFollower(term, false);
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
    }

    @Override
    public void start() {
        becomeFollower(0, false);
    }

    @Override
    public NodeId getNodeId() {
        return this.nodeId;
    }

    @Override
    public void addPeer(Endpoint endpoint) {
        this.cluster.add(endpoint);
    }

    @Override
    public void removePeer(Endpoint endpoint) {
        this.cluster.remove(endpoint);
    }

    @Override
    public void handleElectTimeout() {
        boolean doUnlock = true;
        this.lock.writeLock().lock();
        try {
            if (role != RoleEnum.FOLLOWER) {
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
    public void sendHeartBeat() {
        lock.readLock().lock();
        try {
            if (role != RoleEnum.LEADER) {
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
                AppendEntriesRequest request = AppendEntriesRequest.builder()
                        .term(term)
                        .group(group)
                        .leaderId(id)
                        .toEndpoint(endpoint)
                        .build();
                CompletableFuture<Void> cf = rpcService.appendEntries(endpoint, request)
                        .thenAcceptAsync(resp -> handleHeartBeatResponse(resp, endpoint), executor);
                FutureUtils.addHandleExceptionStage(cf, log);
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    private void handleHeartBeatResponse(AppendEntriesResponse resp, Endpoint from) {
        lock.writeLock().lock();
        try {
            if (resp.getTerm() > term) {
                log.info("Node {} receive heartbeat response with higher term={} from {}, and will become follower", nodeId, resp.getTerm(), from);
                becomeFollower(resp.getTerm(), false);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public AppendEntriesResponse handleAppendEntries(AppendEntriesRequest appendEntries) {
        boolean doUnlock = true;
        lock.writeLock().lock();
        try {
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
                becomeFollower(appendEntries.getTerm() + 1, false);
                return AppendEntriesResponse.builder()
                        .term(appendEntries.getTerm() + 1)
                        .success(false)
                        .build();
            }

            //心跳
            if (appendEntries.getEntries() == null || appendEntries.getEntries().isEmpty()) {
                electTimer.reset();
                return AppendEntriesResponse.builder()
                        .term(term)
                        .success(true)
                        .build();
            }

            //TODO
            log.error("Node {} ignore AppendEntriesRequest from {} because not implement, term={}, currentTerm={}, current leader:{}", nodeId, appendEntries.getLeaderId(), appendEntries.getTerm(), term, leaderId);
            return AppendEntriesResponse.builder()
                    .term(term)
                    .success(false)
                    .build();
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
                        becomeFollower(requestVoteRequest.getTerm(), false);
                    }
                } else {
                    log.info("Node {} ignore RequestVoteRequest from {}, term={}, currentTerm={}", nodeId, requestVoteRequest.getCandidateId(), requestVoteRequest.getTerm(), term);
                    break;
                }
                //TODO check last LogId
                if (votedFor == null ) {
                    this.votedFor = requestVoteRequest.getCandidateId();
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
                if (requestVoteRequest.getTerm() < term) {
                    log.info("Node {} ignore PreVoteRequest from {}, term={}, currentTerm={}", nodeId, requestVoteRequest.getCandidateId(), requestVoteRequest.getTerm(), term);
                    break;
                }
                //TODO check last logId
                granted = true;
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
        //TODO 获取lastLogId
        long oldTerm;
        try {
            log.info("Node {} start preVote, term={}", id, term);
            oldTerm = term;
        } finally {
            lock.writeLock().unlock();
        }

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
//                        .lastLogIndex()
//                        .lastLogTerm()
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
                becomeFollower(resp.getTerm(), false);
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
                becomeFollower(resp.getTerm(), false);
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
                        .toEndpoint(endpoint)
                        .build();
                CompletableFuture<Void> cf = rpcService.requestVote(endpoint, request)
                        .thenAcceptAsync(resp -> handleVoteResponse(resp, term), executor);
                FutureUtils.addHandleExceptionStage(cf, log);
            }
            //TODO storage vote info
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
            becomeFollower(requestTerm, false);
        } else if (this.role != RoleEnum.FOLLOWER) {
            log.warn("Candidate Node {} receive request from {}, term: {}", nodeId, from, requestTerm);
            becomeFollower(requestTerm, false);
        } else if (leaderId == null) {
            log.warn("Follower Node {} receive request from {}, term: {}", nodeId, from, requestTerm);
            becomeFollower(requestTerm, false);
        }
        leaderId = from;
    }

    //方法在writeLock中
    private void becomeFollower(long term, boolean wakeupCandidate) {
        if (role == RoleEnum.CANDIDATE) {
            voteTimeoutTimer.stop();
        }

        this.leaderId = null;
        this.role = RoleEnum.FOLLOWER;

        if (term > this.term) {
            this.term = term;
            this.votedFor = null;
        }
        sendHeartbeatTimer.stop();
        electTimer.reset();
        log.info("Node {} become follower, term: {}, current term: {}", id, term, this.term);
    }

    private void becomeLeader() {
        if (this.role != RoleEnum.CANDIDATE) {
            log.error("Node {} become leader failed, role: {}", id, role);
            return;
        }

        this.role = RoleEnum.LEADER;
        this.leaderId = id;

        this.electTimer.stop();
        this.sendHeartbeatTimer.start();
        log.info("Node {} become leader, term: {}", id, term);
    }
}
