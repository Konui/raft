package cn.marci.raft.core.node;

import cn.marci.raft.common.Endpoint;
import cn.marci.raft.common.Lifecycle;
import cn.marci.raft.core.log.Entry;
import cn.marci.raft.core.rpc.dto.AppendEntriesDTO;
import cn.marci.raft.core.rpc.dto.AppendEntriesResponse;
import cn.marci.raft.core.rpc.dto.RequestVoteDTO;
import cn.marci.raft.core.rpc.dto.RequestVoteResponse;

import java.util.List;

public interface Node extends Lifecycle {

    void startElect();

    void sendHeartBeat();

    AppendEntriesResponse handleAppendEntries(AppendEntriesDTO appendEntries);

    RequestVoteResponse handleVote(RequestVoteDTO requestVoteDTO);

}
