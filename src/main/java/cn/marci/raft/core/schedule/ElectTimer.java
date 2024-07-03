package cn.marci.raft.core.schedule;

import cn.marci.raft.core.node.Node;
import lombok.extern.slf4j.Slf4j;

import java.util.Random;

@Slf4j
public class ElectTimer extends Timer {
    private final Node node;

    private final Random random = new Random();

    private final long electionMinTimout;

    private final long electionMaxTimeout;

    public ElectTimer(Node node, long electionMinTimout, long electionMaxTimeout) {
        if (electionMinTimout > electionMaxTimeout) {
            throw new IllegalArgumentException("electionMinTimout must be less than or equal to electionMaxTimout");
        }
        if (electionMinTimout < 0) {
            throw new IllegalArgumentException("electionMinTimout must be non-negative");
        }

        this.node = node;
        this.electionMinTimout = electionMinTimout;
        this.electionMaxTimeout = electionMaxTimeout;
    }

    @Override
    protected void run() {
        node.startElect();
    }

    @Override
    protected long nextDelay() {
        return random.nextLong(electionMinTimout, electionMaxTimeout);
    }
}
