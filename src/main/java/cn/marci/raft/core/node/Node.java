package cn.marci.raft.core.node;

import cn.marci.raft.common.Endpoint;
import cn.marci.raft.common.Lifecycle;
import cn.marci.raft.core.rpc.dto.AppendEntriesRequest;
import cn.marci.raft.core.rpc.dto.AppendEntriesResponse;
import cn.marci.raft.core.rpc.dto.RequestVoteRequest;
import cn.marci.raft.core.rpc.dto.RequestVoteResponse;

public interface Node extends Lifecycle {

    NodeId getNodeId();

    void addPeer(Endpoint endpoint);

    void removePeer(Endpoint endpoint);

    void handleElectTimeout();

    void sendHeartBeat();

    AppendEntriesResponse handleAppendEntries(AppendEntriesRequest appendEntries);

    RequestVoteResponse handleVote(RequestVoteRequest requestVoteRequest);

    RequestVoteResponse handlePreVote(RequestVoteRequest requestVoteRequest);

}
