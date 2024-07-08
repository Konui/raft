package cn.marci.raft.core;

import cn.marci.raft.core.node.Node;
import cn.marci.raft.core.node.NodeImpl;
import cn.marci.raft.core.node.NodeManager;

import java.util.concurrent.locks.LockSupport;

public class RaftServer {

    public static void main(String[] args) {
        Node node = NodeManager.getInstance().create("A-group");
        node.start();
        LockSupport.park();
    }


}
