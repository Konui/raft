package cn.marci.raft.core.rpc.impl;

import cn.marci.raft.common.Endpoint;
import cn.marci.raft.core.node.Node;
import cn.marci.raft.core.rpc.RpcService;
import cn.marci.raft.core.rpc.dto.AppendEntriesDTO;
import cn.marci.raft.core.rpc.dto.AppendEntriesResponse;
import cn.marci.raft.core.rpc.dto.RequestVoteDTO;
import cn.marci.raft.core.rpc.dto.RequestVoteResponse;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RpcServiceImpl implements RpcService {

    private final Node node;

    public RpcServiceImpl(Node node) {
        this.node = node;
    }

    @Override
    public AppendEntriesResponse appendEntries(Endpoint endpoint, AppendEntriesDTO appendEntries) {
        long start = System.currentTimeMillis();
        try {
            return node.handleAppendEntries(appendEntries);
        } finally {
            log.debug("appendEntries cost: {}ms", System.currentTimeMillis() - start);
        }
    }

    @Override
    public RequestVoteResponse requestVote(Endpoint endpoint, RequestVoteDTO requestVoteDTO) {
        long start = System.currentTimeMillis();
        try {
            return node.handleVote(requestVoteDTO);
        } finally {
            log.debug("requestVote cost: {}ms", System.currentTimeMillis() - start);
        }
    }
}
