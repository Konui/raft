package cn.marci.raft.core.rpc.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

@Data
@Builder
@AllArgsConstructor
public class AppendEntriesResponse implements Serializable {

    private long term;

    private boolean success;

    private long lastLogIndex;

    private int errorCode;
}
