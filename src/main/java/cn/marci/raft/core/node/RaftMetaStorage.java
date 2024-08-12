package cn.marci.raft.core.node;

import cn.marci.raft.common.Endpoint;

public interface RaftMetaStorage {

    boolean setTerm(long term);

    long getTerm();

    boolean setVotedFor(Endpoint endpoint);

    Endpoint getVotedFor();

    boolean setTermAndVotedFor(long term, Endpoint endpoint);
}
