package cn.marci.raft.core.rpc.dto;

import cn.marci.raft.core.node.NodeId;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

@Data
@Builder
public class RequestVoteDTO implements Serializable {

    private long term;

    private NodeId candidateId;

    private long lastLogIndex;

    private long lastLogTerm;

}
