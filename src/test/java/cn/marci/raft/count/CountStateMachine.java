package cn.marci.raft.count;

import cn.marci.raft.core.node.OperationMeta;
import cn.marci.raft.core.node.StateMachine;
import cn.marci.raft.serializer.SerializerSingleFactory;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class CountStateMachine implements StateMachine {

    private AtomicLong value = new AtomicLong();

    private AtomicLong leaderTerm = new AtomicLong(-1);

    @Override
    public void onApply(OperationMeta meta) {
        long result = 0;
        CountCompletableFuture done = (CountCompletableFuture)meta.getDone();
        CountOperation operation = null;
        if (done != null) {
            //leader节点
            operation = done.getCountOperation();
        } else {
            try {
                operation = SerializerSingleFactory.getInstance().deserialize(meta.getData().array());
            } catch (Exception e) {
                log.error("counter operation deserialize error", e);
            }
        }
        if (operation != null) {
            switch (operation.getOpEnum()) {
                case GET -> result = value.get();
                case INCREMENT -> result = value.addAndGet(operation.getData());
            }
            if (done != null) {
                done.complete(CountCompletableFuture.success(result));
            }
        }
    }

    @Override
    public void onLeaderStart(long term) {
        leaderTerm.set(term);
    }

    @Override
    public void onLeaderStop() {
        leaderTerm.set(-1);
    }

    public boolean isLeader() {
        return leaderTerm.get() > 0;
    }
}
