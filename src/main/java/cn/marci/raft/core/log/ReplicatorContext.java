package cn.marci.raft.core.log;

import cn.marci.raft.common.Endpoint;
import cn.marci.raft.core.node.BallotBox;
import cn.marci.raft.core.node.Node;
import cn.marci.raft.core.rpc.RpcService;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ReplicatorContext {

    private String groupId;

    private Endpoint leaderEndpoint;

    private LogManager logManager;

    private Node node;

    private long term;

    private BallotBox ballotBox;

    private RpcService rpcService;
}
