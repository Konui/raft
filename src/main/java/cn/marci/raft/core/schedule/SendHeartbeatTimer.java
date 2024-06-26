package cn.marci.raft.core.schedule;

import cn.marci.raft.core.node.Node;

import java.util.Collections;

public class SendHeartbeatTimer extends Timer {

    private final Node node;

    public SendHeartbeatTimer(Node node) {
        this.node = node;
    }

    @Override
    protected void run() {
        node.appendEntries(Collections.emptyList());
    }

    @Override
    protected long nextDelay() {
        return 100;
    }
}
