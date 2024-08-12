package cn.marci.raft.core.log;

import cn.marci.raft.common.Endpoint;
import cn.marci.raft.core.node.BallotBox;
import cn.marci.raft.core.node.Node;
import cn.marci.raft.core.rpc.RpcService;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ReplicatorGroup {

    private ReplicatorContext context;

    Map<Endpoint, Replicator> replicatorMap = new ConcurrentHashMap<>();

    public ReplicatorGroup(String groupId, Endpoint id, LogManager logManager, Node node, long term, BallotBox ballotBox, RpcService rpcService) {
        this.context = new ReplicatorContext(groupId, id, logManager, node, term, ballotBox, rpcService);
    }

    public boolean addReplicator(Endpoint endpoint) {
        if (context.getLeaderEndpoint().equals(endpoint)) {
            return false;
        }
        Replicator replicator = Replicator.newInstanceAndStart(endpoint, context);
        replicatorMap.put(endpoint, replicator);
        return true;
    }

    public boolean removeReplicator(Endpoint endpoint) {
        Replicator replicator = replicatorMap.remove(endpoint);
        if (replicator != null) {
            replicator.stop();
            return true;
        }
        return false;
    }

    public void resetTerm(long term) {
        if (term < context.getTerm()) {
            return;
        }
        context.setTerm(term);
    }

    public void stopAll() {
        ArrayList<Replicator> replicators = new ArrayList<>(replicatorMap.values());
        this.replicatorMap.clear();
        for (Replicator replicator : replicators) {
            replicator.stop();
        }
    }
}
