package cn.marci.raft.core.node;

import cn.marci.raft.common.Endpoint;
import cn.marci.raft.core.conf.RaftConf;
import cn.marci.raft.core.log.Entry;
import cn.marci.raft.core.rpc.RpcService;
import cn.marci.raft.core.rpc.dto.AppendEntriesDTO;
import cn.marci.raft.core.rpc.dto.AppendEntriesResponse;
import cn.marci.raft.core.rpc.dto.RequestVoteDTO;
import cn.marci.raft.core.rpc.dto.RequestVoteResponse;
import cn.marci.raft.core.schedule.ElectTimer;
import cn.marci.raft.core.schedule.SendHeartbeatTimer;
import cn.marci.raft.utils.NetUtils;
import cn.marci.raft.utils.ThreadPoolUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
public class NodeImpl implements Node {

    private NodeId id;

    private NodeId leaderId;

    private RoleEnum role = RoleEnum.FOLLOWER;

    private List<NodeId> cluster = new CopyOnWriteArrayList<>();

    private long term;

    private Endpoint votedFor;

    private final RaftConf conf;

    private ThreadPoolExecutor executor = new ThreadPoolExecutor(10, 20, 1, TimeUnit.MINUTES, new ArrayBlockingQueue<>(10), ThreadPoolUtils.getThreadFactory(false, "node-executor-"));

    private final RpcService rpcService;

    private ElectTimer electTimer;

    private SendHeartbeatTimer sendHeartbeatTimer;

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
                .collect(Collectors.toList());
        this.electTimer = new ElectTimer(this);
        this.sendHeartbeatTimer = new SendHeartbeatTimer(this);
    }

    @Override
    public void start() {
        electTimer.start();
    }

    @Override
    public long getTerm() {
        return term;
    }

    @Override
    public NodeId getLeaderId() {
        return leaderId;
    }

    @Override
    public void startElect() {
        log.info("start elect");
        if (role != RoleEnum.FOLLOWER) {
            return;
        }

        role = RoleEnum.CANDIDATE;
        sendHeartbeatTimer.cancel();
        votedFor = id.getEndpoint();
        term += 1;
        RequestVoteDTO requestVoteDTO = RequestVoteDTO.builder()
                .term(term)
                .candidateId(id)
//                .lastLogIndex()
//                .lastLogTerm()
                .build();

        int cnt = 0;
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
                    term = requestVoteResponse.getTerm();
                    votedFor = null;
                    return;
                }
            } catch (Exception e) {
                log.error("request vote error", e);
            }
        }


        if (cnt > Math.ceilDiv(cluster.size(), 2)) {
            role = RoleEnum.LEADER;
            leaderId = id;
            sendHeartbeatTimer.start();
        } else {
            role = RoleEnum.FOLLOWER;
            sendHeartbeatTimer.cancel();
        }
        votedFor = null;
        log.info("end elect, role: {}, term: {}", role, term);
    }

    @Override
    public void resetElectionTimeout() {
        electTimer.reset();
    }

    @Override
    public void appendEntries(List<Entry> entries) {
        AppendEntriesDTO appendEntriesDTO = AppendEntriesDTO.builder()
                .term(term)
                .leaderId(id)
//                .prevLogIndex()
//                .prevLogTerm()
                .entries(entries)
//                .leaderCommit()
                .build();
        List<Future<AppendEntriesResponse>> futures = cluster.stream()
                .map(nodeId -> executor.submit(() -> rpcService.appendEntries(nodeId.getEndpoint(), appendEntriesDTO)))
                .toList();
        for (Future<AppendEntriesResponse> future : futures) {
            try {
                AppendEntriesResponse appendEntriesResponse = future.get(100, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                log.error("append entries error", e);
            }
        }
    }

    @Override
    public void voteFor(Endpoint endpoint) {
        votedFor = endpoint;
    }

    @Override
    public Endpoint getVotedFor() {
        return votedFor;
    }
}
