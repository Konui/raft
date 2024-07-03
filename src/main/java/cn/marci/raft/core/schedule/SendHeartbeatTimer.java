package cn.marci.raft.core.schedule;

import cn.marci.raft.core.node.Node;

import java.util.Collections;

public class SendHeartbeatTimer extends Timer {

    private final Node node;

    private final long heartbeatInterval;

    public SendHeartbeatTimer(Node node, long heartbeatInterval) {
        if (heartbeatInterval < 0) {
            throw new IllegalArgumentException("heartbeatInterval must be non-negative");
        }
        this.heartbeatInterval = heartbeatInterval;
        this.node = node;
    }

    @Override
    protected void run() {
        node.sendHeartBeat();
    }

    @Override
    protected long nextDelay() {
        return heartbeatInterval;
    }
}
