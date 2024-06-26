package cn.marci.raft.core.rpc;

import cn.marci.raft.common.Endpoint;
import cn.marci.raft.core.rpc.dto.AppendEntriesDTO;
import cn.marci.raft.core.rpc.dto.AppendEntriesResponse;
import cn.marci.raft.core.rpc.dto.RequestVoteDTO;
import cn.marci.raft.core.rpc.dto.RequestVoteResponse;
import cn.marci.raft.rpc.RpcClient;

public interface RpcService {

    AppendEntriesResponse appendEntries(Endpoint endpoint, AppendEntriesDTO appendEntries);

    RequestVoteResponse requestVote(Endpoint endpoint, RequestVoteDTO requestVoteDTO);

}
