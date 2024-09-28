package cn.marci.raft.core.rpc.dto;

import cn.marci.raft.common.Endpoint;
import cn.marci.raft.core.log.EntryMeta;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
public class AppendEntriesRequest implements Serializable {

    private String group;

    private Endpoint toEndpoint;

    private long term;

    private Endpoint leaderId;

    private long prevLogIndex;

    private long prevLogTerm;

    private List<EntryMeta> entries;

    private long lastCommittedIndex;
}
