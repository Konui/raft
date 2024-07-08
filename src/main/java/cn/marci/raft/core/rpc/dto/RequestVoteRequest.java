package cn.marci.raft.core.rpc.dto;

import cn.marci.raft.common.Endpoint;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

@Data
@Builder
public class RequestVoteRequest implements Serializable {

    private String group;

    private Endpoint toEndpoint;

    private boolean preVote;

    private long term;

    private Endpoint candidateId;

    private long lastLogIndex;

    private long lastLogTerm;

}
