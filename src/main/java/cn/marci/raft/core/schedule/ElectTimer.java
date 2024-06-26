package cn.marci.raft.core.schedule;

import cn.marci.raft.core.node.Node;
import lombok.extern.slf4j.Slf4j;

import java.util.Random;

@Slf4j
public class ElectTimer extends Timer {
    private final Node node;

    private final Random random = new Random();


    public ElectTimer(Node node) {
        this.node = node;
    }

    @Override
    protected void run() {
        node.startElect();
    }

    @Override
    protected long nextDelay() {
        return random.nextLong(150, 350);
    }
}
