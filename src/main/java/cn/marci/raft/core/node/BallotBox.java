package cn.marci.raft.core.node;

import cn.marci.raft.common.Endpoint;
import cn.marci.raft.common.Lifecycle;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Slf4j
public class BallotBox implements Lifecycle {

    private long pendingIndex;

    private long lastCommittedIndex;

    private ArrayList<Ballot> pendingQueue = new ArrayList<>();

    private CallbackQueue callbackQueue;

    ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private FSMCaller fsmCaller;

    public void init(FSMCaller fsmCaller, CallbackQueue callbackQueue) {
        this.fsmCaller = fsmCaller;
        this.callbackQueue = callbackQueue;
    }

    @Override
    public void stop() {
        clearPendingTasks();
    }

    public boolean commitAt(long firstLogIndex, long lastLogIndex, Endpoint endpoint) {
        lock.writeLock().lock();
        try {
            long lastCommittedIndex = 0;
            if (this.pendingIndex == 0) {
                return false;
            }
            if (lastLogIndex < this.pendingIndex) {
                return true;
            }
            if (lastLogIndex >= this.pendingIndex + pendingQueue.size()) {
                throw new ArrayIndexOutOfBoundsException();
            }
            long startAt = Math.max(this.pendingIndex, firstLogIndex);
            for (long logIndex = startAt; logIndex <= lastLogIndex; logIndex++) {
                Ballot ballot = this.pendingQueue.get((int) (logIndex - this.pendingIndex));
                ballot.grant(endpoint);
                if (ballot.isGranted()) {
                    lastCommittedIndex = logIndex;
                }
            }
            if (lastCommittedIndex == 0) {
                return true;
            }
            for (int i = 0; i<=lastCommittedIndex - this.pendingIndex; i++) {
                this.pendingQueue.remove(i);
            }
            this.pendingIndex = lastCommittedIndex + 1;
            this.lastCommittedIndex = lastCommittedIndex;
            fsmCaller.onCommitted(this.lastCommittedIndex);
            if (log.isDebugEnabled()) {
                log.debug("BallotBox commitAt, firstLogIndex: {}, lastLogIndex: {}, endpoint: {}", firstLogIndex, lastLogIndex, endpoint);
            }
        } finally {
            lock.writeLock().unlock();
        }
        return true;
    }

    public boolean appendPendingTask(List<Endpoint> cluster, CompletableFuture<?> done) {
        Ballot bl = new Ballot(cluster);
        lock.writeLock().lock();
        try {
            if (this.pendingIndex <= 0) {
                log.error("pendingIndex is less than 0, pendingIndex: {}", this.pendingIndex);
                return false;
            }
            this.pendingQueue.add(bl);
            this.callbackQueue.addPendingCallback(done);
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean clearPendingTasks() {
        lock.writeLock().lock();
        try {
            this.pendingIndex = 0;
            this.pendingQueue.clear();
            this.callbackQueue.clear();
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean resetPendingIndex(long newPendingIndex) {
        lock.writeLock().lock();
        try {
            if (!(this.pendingIndex == 0 && this.pendingQueue.isEmpty())) {
                log.error("pendingIndex is not empty, pendingIndex: {}, pendingQueue: {}", this.pendingIndex, this.pendingQueue.size());
                return false;
            }
            if (newPendingIndex <= this.lastCommittedIndex) {
                log.error("newPendingIndex is less than lastCommittedIndex, newPendingIndex: {}, lastCommittedIndex: {}", newPendingIndex, this.lastCommittedIndex);
                return false;
            }
            this.pendingIndex = newPendingIndex;
            this.callbackQueue.resetFirstIndex(newPendingIndex);
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public long getLastCommittedIndex() {
        lock.readLock().lock();
        try {
            return this.lastCommittedIndex;
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean setLastCommittedIndex(long lastCommittedIndex) {
        lock.writeLock().lock();
        try {
            if (this.pendingIndex != 0 || !this.pendingQueue.isEmpty()) {
                log.error("pendingIndex is not empty, pendingIndex: {}, pendingQueue: {}", this.pendingIndex, this.pendingQueue.size());
                return false;
            }
            if (lastCommittedIndex < this.lastCommittedIndex) {
                return false;
            }
            this.lastCommittedIndex = lastCommittedIndex;
            fsmCaller.onCommitted(this.lastCommittedIndex);
            if (log.isDebugEnabled()) {
                log.debug("BallotBox updateLastCommittedIndex, lastLogIndex: {}", this.lastCommittedIndex);
            }
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

}
