package cn.marci.raft.core.node.impl;

import cn.marci.raft.common.Lifecycle;
import cn.marci.raft.core.log.LogEntry;
import cn.marci.raft.core.log.LogId;
import cn.marci.raft.core.log.LogManager;
import cn.marci.raft.core.node.CallbackQueue;
import cn.marci.raft.core.node.FSMCaller;
import cn.marci.raft.core.node.OperationMeta;
import cn.marci.raft.core.node.StateMachine;
import cn.marci.raft.utils.ThreadPoolUtils;
import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.FatalExceptionHandler;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class FSMCallerImpl implements FSMCaller, Lifecycle {

    private StateMachine stateMachine;

    private LogManager logManager;

    private long lastAppliedTerm;

    private AtomicLong lastApplyIndex = new AtomicLong();

    private AtomicLong lastCommittedIndex = new AtomicLong();

    private Disruptor<CommittedEvent> committedQueue;

    private CallbackQueue callbackQueue;

    public FSMCallerImpl() {
        this.committedQueue = new Disruptor<>(CommittedEvent::new, 1024,
                ThreadPoolUtils.getThreadFactory(false, "FSMCallerImpl"),
                ProducerType.MULTI,
                new BlockingWaitStrategy());
        this.committedQueue.handleEventsWith(new CommittedEventHandler());
        this.committedQueue.setDefaultExceptionHandler(new FatalExceptionHandler());
    }

    @Override
    public void init(CallbackQueue callbackQueue, StateMachine stateMachine, LogManager logManager) {
        this.callbackQueue = callbackQueue;
        this.stateMachine = stateMachine;
        this.logManager = logManager;
        start();
    }

    @Override
    public void start() {
        this.committedQueue.start();
    }

    @Override
    public void stop() {
        callbackQueue.clear();
        this.committedQueue.shutdown();
    }

    @Override
    public boolean onCommitted(long committedIndex) {
        committedQueue.publishEvent((event, sequence) -> {
            event.committedIndex = committedIndex;
        });
        return true;
    }

    @Override
    public void onLeaderStart(long term) {
        stateMachine.onLeaderStart(term);
    }

    @Override
    public void onLeaderStop() {
        stateMachine.onLeaderStop();
    }

    public void doCommitted(long committedIndex) {
        if (lastApplyIndex.get() >= committedIndex) {
            return;
        }
        lastCommittedIndex.set(committedIndex);
        List<CompletableFuture<?>> list = new ArrayList<>();
        long firstCallbackIndex = callbackQueue.popPendingCallback(committedIndex, list);
        if (firstCallbackIndex < 0) {
            log.warn("invalid firstCallbackIndex:{}", firstCallbackIndex);
            return;
        }
        long curIndex = lastApplyIndex.get() + 1;
        for (; curIndex <= committedIndex; curIndex++) {
            CompletableFuture<?> cf = curIndex < firstCallbackIndex ? null : list.get((int) (curIndex - firstCallbackIndex));
            LogEntry logEntry = logManager.getLogEntry(curIndex);
            if (logEntry == null) {
                log.warn("log entry not found, index:{}", curIndex);
                break;
            }
            stateMachine.onApply(new OperationMeta(cf, logEntry.getData()));
        }
        long lastTerm = logManager.getTerm(curIndex);
        final LogId lastAppliedId = new LogId(curIndex, lastTerm);
        this.lastApplyIndex.set(committedIndex);
        this.lastAppliedTerm = lastTerm;
        this.logManager.setAppliedId(lastAppliedId);
    }

    @Data
    private static class CommittedEvent {
        private long committedIndex;
    }

    private class CommittedEventHandler implements EventHandler<CommittedEvent> {
        @Override
        public void onEvent(CommittedEvent committedEvent, long sequence, boolean endOfBatch) throws Exception {
            if (endOfBatch) {
                doCommitted(committedEvent.getCommittedIndex());
            }
        }
    }

}
