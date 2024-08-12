package cn.marci.raft.core.node;

import cn.marci.raft.common.Endpoint;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Ballot {

    private int quorum;

    private int granted;

    private Set<Endpoint> votedSet = new HashSet<>();


    public Ballot(List<Endpoint> cluster) {
        this.quorum = cluster.size() / 2 + 1;
        this.granted = 0;
    }

    public void grant(Endpoint endpoint) {
        if (votedSet.contains(endpoint)) {
            return;
        }
        votedSet.add(endpoint);
        this.granted++;
    }

    public boolean isGranted() {
        return granted >= quorum;
    }
}
