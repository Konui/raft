package cn.marci.raft.core.node;

import cn.marci.raft.common.Endpoint;

import java.util.concurrent.ConcurrentHashMap;

public class NodeManager {


    private static final NodeManager INSTANCE = new NodeManager();

    private final ConcurrentHashMap<NodeId, Node> nodeMap = new ConcurrentHashMap<>();

    public static NodeManager getInstance() {
        return INSTANCE;
    }

    {
        Runtime.getRuntime().addShutdownHook(new Thread(() ->  nodeMap.values().forEach(Node::stop)));
    }

    public Node get(NodeId nodeId) {
        return nodeMap.get(nodeId);
    }

    public Node get(String group, Endpoint endpoint) {
        return get(new NodeId(group, endpoint));
    }

    public Node create(String group, StateMachine sm) {
        Node node = new NodeImpl(group, sm);
        Node existNode = nodeMap.putIfAbsent(node.getNodeId(), node);
        if (existNode != null) {
            throw new IllegalArgumentException("node already exist:" + existNode.getNodeId());
        }
        return node;
    }
}
