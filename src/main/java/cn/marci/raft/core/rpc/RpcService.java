package cn.marci.raft.core.rpc;

import cn.marci.raft.common.Endpoint;
import cn.marci.raft.core.rpc.dto.*;
import cn.marci.raft.rpc.netty.Connection;

import java.util.concurrent.CompletableFuture;

public interface RpcService {

    boolean connect(Endpoint endpoint);

    Connection getConnection(Endpoint endpoint);

    CompletableFuture<AppendEntriesResponse> appendEntries(Endpoint endpoint, AppendEntriesRequest appendEntries);

    CompletableFuture<RequestVoteResponse> requestVote(Endpoint endpoint, RequestVoteRequest requestVoteRequest);

    void addPeer(Endpoint from, ClusterRequest.AddPeerRequest request);

    void removePeer(Endpoint from, ClusterRequest.RemovePeerRequest request);
}
