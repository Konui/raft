package cn.marci.raft.core.node;

import cn.marci.raft.common.Endpoint;
import cn.marci.raft.common.Lifecycle;
import cn.marci.raft.core.rpc.dto.AppendEntriesRequest;
import cn.marci.raft.core.rpc.dto.AppendEntriesResponse;
import cn.marci.raft.core.rpc.dto.RequestVoteRequest;
import cn.marci.raft.core.rpc.dto.RequestVoteResponse;

import java.util.function.Consumer;

public interface Node extends Lifecycle {

    NodeId getNodeId();

    void addPeer(Endpoint endpoint);

    void removePeer(Endpoint endpoint);

    void handleElectTimeout();

    AppendEntriesResponse handleAppendEntries(AppendEntriesRequest appendEntries, Consumer<Object> sendRpcRespCallback);

    RequestVoteResponse handleVote(RequestVoteRequest requestVoteRequest);

    RequestVoteResponse handlePreVote(RequestVoteRequest requestVoteRequest);

    void increaseTermTo(long term);

    void apply(Task task);

}
