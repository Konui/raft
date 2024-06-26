package cn.marci.raft.core.rpc.dto;

import cn.marci.raft.common.Endpoint;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

@Data
@AllArgsConstructor
public class RequestVoteResponse implements Serializable {

    private long term;

    private boolean voteGranted;

}
