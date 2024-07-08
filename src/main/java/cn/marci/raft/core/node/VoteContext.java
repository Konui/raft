package cn.marci.raft.core.node;

import cn.marci.raft.common.Endpoint;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
public class VoteContext {

    private final boolean preVote;

    private long term;

    private int quorum;

    private int granted;

    public VoteContext(boolean preVote) {
        this.preVote = preVote;
    }

    private Set<Endpoint> votedSet = new HashSet<>();

    public void init(long term, List<Endpoint> cluster) {
        this.term = term;
        this.votedSet.clear();
        this.quorum = Math.ceilDiv(cluster.size(), 2);
        this.granted = 0;
    }

    public void grant(Endpoint endpoint) {
        if (votedSet.contains(endpoint)) {
            log.warn("{} has already {} for term={}", endpoint, preVote ? "preVote" : "vote", term);
            return;
        }
        votedSet.add(endpoint);
        this.granted++;
        log.info("{} {} for term={}, quorum={}, granted={}", endpoint, preVote ? "preVote" : "vote", term, quorum, granted);
    }

    public boolean isGranted() {
        return granted >= quorum;
    }
}
