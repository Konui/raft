package cn.marci.raft.core.node;

import cn.marci.raft.common.Endpoint;
import cn.marci.raft.core.conf.RaftConf;
import cn.marci.raft.core.rpc.RpcService;
import cn.marci.raft.core.rpc.dto.AppendEntriesDTO;
import cn.marci.raft.core.rpc.dto.AppendEntriesResponse;
import cn.marci.raft.core.rpc.dto.RequestVoteDTO;
import cn.marci.raft.core.rpc.dto.RequestVoteResponse;
import cn.marci.raft.core.schedule.ElectTimer;
import cn.marci.raft.core.schedule.SendHeartbeatTimer;
import cn.marci.raft.core.schedule.Timer;
import cn.marci.raft.utils.NetUtils;
import cn.marci.raft.utils.ThreadPoolUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

@Slf4j
public class NodeImpl implements Node {

    private NodeId id;

    private volatile NodeId leaderId;

    private RoleEnum role = RoleEnum.FOLLOWER;

    private List<NodeId> cluster = new CopyOnWriteArrayList<>();

    private volatile long term;

    private volatile NodeId votedFor;

    private final RaftConf conf;

    private ThreadPoolExecutor executor = new ThreadPoolExecutor(10, 20, 1, TimeUnit.MINUTES, new ArrayBlockingQueue<>(10), ThreadPoolUtils.getThreadFactory(false, "node-executor-"));

    private final RpcService rpcService;

    private Timer electTimer;

    private Timer sendHeartbeatTimer;

    private ReadWriteLock lock = new ReentrantReadWriteLock();

    public NodeImpl(RaftConf conf, RpcService rpcService) {
        this.conf = conf;
        this.rpcService = rpcService;
        if (conf.getNodes() == null || conf.getNodes().isEmpty()) {
            throw new IllegalArgumentException("raft.nodes is null or empty");
        }
        this.id = new NodeId(null, new Endpoint(NetUtils.getLocalIp(), conf.getRpcServerPort()));
        this.cluster = Arrays.stream(conf.getNodes().split(",|;"))
                .map(Endpoint::new)
                .map(endpoint -> new NodeId(null, endpoint))
                .filter(nodeId -> !nodeId.equals(id))
                .filter(nodeId -> !(NetUtils.isLocalhost(nodeId.getEndpoint().getIp()) && nodeId.getEndpoint().getPort() == id.getEndpoint().getPort()))
                .collect(Collectors.toList());
        this.electTimer = new ElectTimer(this, conf.getElectionMinTimeout(), conf.getElectionMaxTimeout());
        this.sendHeartbeatTimer = new SendHeartbeatTimer(this, conf.getHeartbeatInterval());
    }

    @Override
    public void start() {
        electTimer.start();
    }

    @Override
    public void startElect() {
        if (role != RoleEnum.FOLLOWER) {
            return;
        }
        lock.writeLock().lock();
        try {
            if (role != RoleEnum.FOLLOWER) {
                return;
            }
            log.info("start elect");
            term += 1;
            votedFor = id;
            role = RoleEnum.CANDIDATE;
            sendHeartbeatTimer.cancel();

            RequestVoteDTO requestVoteDTO = RequestVoteDTO.builder()
                    .term(term)
                    .candidateId(id)
//                .lastLogIndex()
//                .lastLogTerm()
                    .build();

            int cnt = 1;
            List<Future<RequestVoteResponse>> futures = cluster.stream()
                    .map(nodeId -> executor.submit(() -> rpcService.requestVote(nodeId.getEndpoint(), requestVoteDTO)))
                    .toList();

            for (Future<RequestVoteResponse> future : futures) {
                try {
                    RequestVoteResponse requestVoteResponse = future.get(100, TimeUnit.MILLISECONDS);
                    if (requestVoteResponse.isVoteGranted()) {
                        cnt++;
                    } else if (requestVoteResponse.getTerm() > term) {
                        role = RoleEnum.FOLLOWER;
                        sendHeartbeatTimer.cancel();
                        votedFor = null;
                        log.info("end elect, cluster has new leader, term:{}", term);
                        return;
                    }
                } catch (Exception e) {
                    log.error("request vote error, exception:{}, msg:{}", e.getClass().getName(), e.getMessage());
                }
            }


            if (cnt > Math.ceilDiv(cluster.size(), 2)) {
                role = RoleEnum.LEADER;
                leaderId = id;
                sendHeartBeat();
                sendHeartbeatTimer.start();
            } else {
                leaderId = null;
                role = RoleEnum.FOLLOWER;
                sendHeartbeatTimer.cancel();
            }
            log.info("end elect, role: {}, term: {}, hadVoted:{}", role, term, cnt);
        } catch (Exception e) {
            log.error("elect error", e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void sendHeartBeat() {
        if (role != RoleEnum.LEADER) {
            return;
        }
        electTimer.reset();
        cluster.forEach(nodeId -> executor.submit(() -> {
                    rpcService.appendEntries(nodeId.getEndpoint(), new AppendEntriesDTO(term, id, 0, 0, Collections.emptyList(), 0));
                }));
    }

    @Override
    public AppendEntriesResponse handleAppendEntries(AppendEntriesDTO appendEntries) {
        lock.writeLock().lock();
        try {
            boolean isLeaderMsg = leaderId == null || Objects.equals(appendEntries.getLeaderId(), leaderId);
            if (appendEntries.getTerm() < term || term == appendEntries.getTerm() && !isLeaderMsg) {
                log.debug("receive invalid append entries, term: {}, leaderId: {}", appendEntries.getTerm(), appendEntries.getLeaderId());
                return new AppendEntriesResponse(term, false);
            }
            //或者term比本机大
            if (appendEntries.getEntries().isEmpty()) {
                log.debug("receive heartbeat, term: {}, leaderId: {}", term, leaderId);
                electTimer.reset();
                //落后集群了
                if (appendEntries.getTerm() > term) {
                    term = appendEntries.getTerm();
                    votedFor = null;
                    leaderId = null;
                    if (!isLeaderMsg) {
                        role = RoleEnum.FOLLOWER;
                        sendHeartbeatTimer.cancel();
                    }
                }
                if (leaderId == null) {
                    log.info("cluster has new leader or term, term:{}, leaderId: {}", term, appendEntries.getLeaderId());
                }
                leaderId = appendEntries.getLeaderId();
                return new AppendEntriesResponse(term, true);
            }
            //TODO process
            return new AppendEntriesResponse(term, true);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public RequestVoteResponse handleVote(RequestVoteDTO requestVoteDTO) {
        lock.writeLock().lock();
        try {
            if (requestVoteDTO.getTerm() > term || (requestVoteDTO.getTerm() == term && requestVoteDTO.getCandidateId().equals(votedFor))) {
                    term = requestVoteDTO.getTerm();
                    votedFor = requestVoteDTO.getCandidateId();
                    role = RoleEnum.FOLLOWER;
                    leaderId = null;
                    sendHeartbeatTimer.cancel();
                    electTimer.reset();
                    return new RequestVoteResponse(requestVoteDTO.getTerm(), true);

            }
            return new RequestVoteResponse(term, false);
        } finally {
            lock.writeLock().unlock();
        }
    }
}
