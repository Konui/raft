package cn.marci.raft.core.rpc;

import cn.marci.raft.common.Endpoint;
import cn.marci.raft.core.rpc.dto.AppendEntriesRequest;
import cn.marci.raft.core.rpc.dto.AppendEntriesResponse;
import cn.marci.raft.core.rpc.dto.RequestVoteRequest;
import cn.marci.raft.core.rpc.dto.RequestVoteResponse;

import java.util.concurrent.CompletableFuture;

public interface RpcService {

    boolean connect(Endpoint endpoint);

    CompletableFuture<AppendEntriesResponse> appendEntries(Endpoint endpoint, AppendEntriesRequest appendEntries);

    CompletableFuture<RequestVoteResponse> requestVote(Endpoint endpoint, RequestVoteRequest requestVoteRequest);

}
