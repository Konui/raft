package cn.marci.raft.core.rpc.dto;

import cn.marci.raft.core.node.NodeId;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RequestVoteDTO {

    private long term;

    private NodeId candidateId;

    private long lastLogIndex;

    private long lastLogTerm;

}
