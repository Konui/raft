package cn.marci.raft.core.rpc.impl;

import cn.marci.raft.common.Endpoint;
import cn.marci.raft.core.node.Node;
import cn.marci.raft.core.rpc.RpcService;
import cn.marci.raft.core.rpc.dto.AppendEntriesDTO;
import cn.marci.raft.core.rpc.dto.AppendEntriesResponse;
import cn.marci.raft.core.rpc.dto.RequestVoteDTO;
import cn.marci.raft.core.rpc.dto.RequestVoteResponse;

public class RpcServiceImpl implements RpcService {

    private final Node node;

    public RpcServiceImpl(Node node) {
        this.node = node;
    }

    @Override
    public AppendEntriesResponse appendEntries(Endpoint endpoint, AppendEntriesDTO appendEntries) {
        // 过期leader请求，不予处理
        if (appendEntries.getTerm() < node.getTerm()) {
            return new AppendEntriesResponse(node.getTerm(), false);
        }
        node.resetElectionTimeout();
        //TODO 添加日志
        return new AppendEntriesResponse(node.getTerm(), true);
    }

    @Override
    public RequestVoteResponse requestVote(Endpoint endpoint, RequestVoteDTO requestVoteDTO) {
        if (requestVoteDTO.getTerm() <= node.getTerm()) {
            return new RequestVoteResponse(node.getTerm(), false);
        }
        node.voteFor(endpoint);
        return new RequestVoteResponse(node.getTerm(), true);
    }
}
