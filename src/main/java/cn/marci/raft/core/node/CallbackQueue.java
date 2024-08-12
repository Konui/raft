package cn.marci.raft.core.node;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class CallbackQueue {

    private Lock lock = new ReentrantLock();

    private LinkedList<CompletableFuture<?>> queue = new LinkedList<>();

    private long firstIndex = 0;

    public void clear() {
        List<CompletableFuture<?>> saveList = new ArrayList<>();

        lock.lock();
        try {
            this.firstIndex = 0;
            saveList = queue;
            this.queue = new LinkedList<>();
        } finally {
            lock.unlock();
        }
        saveList.forEach(completableFuture -> completableFuture.completeExceptionally(new IllegalStateException("Leader stepped down")));
    }

    public void resetFirstIndex(long firstIndex) {
        lock.lock();
        try {
            if (!queue.isEmpty()) {
                throw new IllegalStateException("queue is not empty");
            }
            this.firstIndex = firstIndex;
        } finally {
            lock.unlock();
        }
    }

    public void addPendingCallback(CompletableFuture<?> cf) {
        lock.lock();
        try {
            this.queue.add(cf);
        } finally {
            lock.unlock();
        }
    }

    public long popPendingCallback(long endIndex, List<CompletableFuture<?>> list) {
        list.clear();
        lock.lock();
        try {
            final int queueSize = this.queue.size();
            if (queueSize == 0 || endIndex < this.firstIndex) {
                return endIndex + 1;
            }
            if (endIndex > this.firstIndex + queueSize - 1) {
                return -1;
            }
            final long outFirstIndex = this.firstIndex;
            for (long i = outFirstIndex; i <= endIndex; i++) {
                list.add(this.queue.pollFirst());
            }
            this.firstIndex = endIndex + 1;
            return outFirstIndex;
        } finally {
            lock.unlock();
        }
    }
}
