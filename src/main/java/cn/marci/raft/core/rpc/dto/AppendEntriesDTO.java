package cn.marci.raft.core.rpc.dto;

import cn.marci.raft.core.log.Entry;
import cn.marci.raft.core.node.NodeId;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
public class AppendEntriesDTO implements Serializable {

    private long term;

    private NodeId leaderId;

    private long prevLogIndex;

    private long prevLogTerm;

    private List<Entry> entries;

    private long leaderCommit;
}
