package cn.marci.raft.rpc.netty;

import java.util.concurrent.atomic.AtomicLong;

public class IDGenerator {

    private static final AtomicLong ID_GENERATOR = new AtomicLong(0);

    public static long nextId() {
        return ID_GENERATOR.incrementAndGet();
    }
}
