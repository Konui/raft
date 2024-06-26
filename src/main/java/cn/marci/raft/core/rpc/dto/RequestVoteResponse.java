package cn.marci.raft.core.rpc.dto;

import cn.marci.raft.common.Endpoint;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RequestVoteResponse {

    private long term;

    private boolean voteGranted;

}
