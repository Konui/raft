package cn.marci.raft.core;

import cn.marci.raft.core.node.*;

import java.util.concurrent.locks.LockSupport;

public class RaftServer {

    public static void main(String[] args) {
        Node node = NodeManager.getInstance().create("A-group", new StateMachine() {
            @Override
            public void onApply(OperationMeta meta) {

            }

            @Override
            public void onLeaderStart(long term) {

            }

            @Override
            public void onLeaderStop() {

            }
        });
        node.start();
        LockSupport.park();
    }


}
