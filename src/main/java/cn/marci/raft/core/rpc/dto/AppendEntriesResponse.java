package cn.marci.raft.core.rpc.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AppendEntriesResponse {

    private long term;

    private boolean success;
}
