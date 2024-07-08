package cn.marci.raft.core.rpc.impl;

import cn.marci.raft.common.Endpoint;
import cn.marci.raft.core.rpc.RaftRpcFactory;
import cn.marci.raft.core.rpc.RpcService;
import cn.marci.raft.core.rpc.dto.AppendEntriesRequest;
import cn.marci.raft.core.rpc.dto.AppendEntriesResponse;
import cn.marci.raft.core.rpc.dto.RequestVoteRequest;
import cn.marci.raft.core.rpc.dto.RequestVoteResponse;
import cn.marci.raft.rpc.RpcClient;
import cn.marci.raft.rpc.RpcFactory;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;

@Slf4j
public class RpcServiceImpl implements RpcService {

    private final RpcClient rpcClient;

    public RpcServiceImpl(RpcClient rpcClient) {
        this.rpcClient = rpcClient;
    }

    @Override
    public boolean connect(Endpoint endpoint) {
        try {
            RpcFactory.getInstance().connect(endpoint);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public CompletableFuture<AppendEntriesResponse> appendEntries(Endpoint endpoint, AppendEntriesRequest appendEntries) {
        return rpcClient.invokeAsync(endpoint, appendEntries);
    }

    @Override
    public CompletableFuture<RequestVoteResponse> requestVote(Endpoint endpoint, RequestVoteRequest requestVoteRequest) {
        return rpcClient.invokeAsync(endpoint, requestVoteRequest);
    }
}
