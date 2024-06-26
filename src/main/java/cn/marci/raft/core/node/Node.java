package cn.marci.raft.core.node;

import cn.marci.raft.common.Endpoint;
import cn.marci.raft.common.Lifecycle;
import cn.marci.raft.core.log.Entry;

import java.util.List;

public interface Node extends Lifecycle {

    long getTerm();

    NodeId getLeaderId();

    void startElect();

    void resetElectionTimeout();

    void appendEntries(List<Entry> entries);

    void voteFor(Endpoint endpoint);

    Endpoint getVotedFor();

}
