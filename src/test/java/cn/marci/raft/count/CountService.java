package cn.marci.raft.count;

import cn.marci.raft.core.node.Task;
import cn.marci.raft.serializer.SerializerSingleFactory;
import lombok.extern.slf4j.Slf4j;

import java.nio.ByteBuffer;

@Slf4j
public class CountService {

    private final CountServer countServer;

    public CountService(CountServer countServer) {
        this.countServer = countServer;
    }

    public void get(CountCompletableFuture done) {
        applyOp(new CountOperation(CountOperation.Operation.GET.ordinal()), done);
    }

    public void increment(long data, CountCompletableFuture done)  {
        applyOp(new CountOperation(CountOperation.Operation.INCREMENT.ordinal(), data), done);
    }

    public void applyOp(CountOperation countOperation, CountCompletableFuture done) {
        if (!this.countServer.getCsm().isLeader()) {
            done.complete(CountCompletableFuture.failure("not leader",  null));
            return;
        }
        try {
            done.setCountOperation(countOperation);
            Task task = new Task(ByteBuffer.wrap(SerializerSingleFactory.getInstance().serialize(countOperation)), done);
            this.countServer.getNode().apply(task);
        } catch (Exception e) {
            log.error("count service apply op error", e);
            done.complete(CountCompletableFuture.failure(e.getMessage(), null));
        }
    }
}
