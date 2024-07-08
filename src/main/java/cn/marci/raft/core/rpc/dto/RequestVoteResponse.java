package cn.marci.raft.core.rpc.dto;

import cn.marci.raft.common.Endpoint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

@Data
@Builder
@AllArgsConstructor
public class RequestVoteResponse implements Serializable {

    private long term;

    private boolean voteGranted;

    private Endpoint from;

}
