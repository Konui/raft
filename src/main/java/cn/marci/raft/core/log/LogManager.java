package cn.marci.raft.core.log;

import cn.marci.raft.common.Lifecycle;
import cn.marci.raft.core.conf.RaftConf;
import cn.marci.raft.core.log.impl.RocksDBLogStorage;
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
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Slf4j
public class LogManager implements Lifecycle {

    private ThreadPoolExecutor executor = new ThreadPoolExecutor(10, 20, 1, TimeUnit.MINUTES, new LinkedBlockingQueue<>(100), ThreadPoolUtils.getThreadFactory(false, "LogManager-apply-"));

    private LogStorage logStorage;

    private volatile long firstLogIndex;

    private volatile long lastLogIndex;

    private volatile LogId applied = new LogId(0, 0);

    private volatile LogId diskId = new LogId(0, 0);

    private final Disruptor<AppendLogEntriesEvent> disruptor;

    private ConcurrentLinkedDeque<LogEntry> logsInMemory = new ConcurrentLinkedDeque<>();

    private ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private AtomicLong nextWaitId = new AtomicLong(1);

    private Map<Long, Runnable> waitMap = new ConcurrentHashMap();

    public LogManager() {
        this.disruptor = new Disruptor<>(AppendLogEntriesEvent::new,
                1024,
                ThreadPoolUtils.getThreadFactory(false, "LogManager-disruptor-"),
                ProducerType.MULTI,
                new BlockingWaitStrategy());
        this.disruptor.handleEventsWith(new AppendEntriesHandler());
        this.disruptor.setDefaultExceptionHandler(new FatalExceptionHandler());
    }

    public boolean init(String filePath) {
        lock.writeLock().lock();
        try {
            this.logStorage = new RocksDBLogStorage(filePath);
            this.firstLogIndex = logStorage.getFirstLogIndex();
            this.lastLogIndex = logStorage.getLastLogIndex();
            this.diskId = new LogId(getTerm(this.lastLogIndex), this.lastLogIndex);
            this.disruptor.start();
        } finally {
            lock.writeLock().unlock();
        }
        return true;
    }

    @Override
    public void stop() {
        disruptor.shutdown();
        logStorage.stop();
    }

    public void appendEntries(List<LogEntry> entries, StableCallback callback) {
        lock.writeLock().lock();
        try {
            if (!entries.isEmpty() && !checkAndResolveConflict(entries)) {
                executor.submit(() -> callback.run(false));
                entries.clear();
                return;
            }

            if (!entries.isEmpty()) {
                callback.setFirstLogIndex(entries.get(0).getId().getIndex());
                logsInMemory.addAll(entries);
            }
            callback.setEntries(entries);
            wakeupAllWaiter();
            this.disruptor.getRingBuffer().publishEvent((event, seq) -> {
                event.reset();
                event.entries = entries;
                event.callback = callback;
            });
            if (log.isDebugEnabled()) {
                log.debug("logManager published append entries event");
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private boolean checkAndResolveConflict(List<LogEntry> entries) {
        LogEntry firstLog = entries.getFirst();
        //说明是leader节点，因为从leader节点发送的请求会有index，而由leader节点生成的到目前未设置index
        if (firstLog.getId().getIndex() == 0) {
            for (int i = 0; i < entries.size(); i++) {
                entries.get(i).getId().setIndex(++this.lastLogIndex);
            }
            return true;
        }
        //follower节点检查并解决冲突
        //待写入的日志太新，中间有日志还没有复制
        if (firstLog.getId().getIndex() > this.lastLogIndex + 1) {
            log.warn("logManager received append entries request, but the first log index is too new, lastLogIndex={}, writeFirstLogIndex={}", this.lastLogIndex + 1, firstLog.getId().getIndex());
            return false;
        }
        LogEntry lastLog = entries.getLast();
        //待写入的太旧
        if (lastLog.getId().getIndex() < this.applied.getIndex()) {
            log.warn("logManager received append entries request, but the last log index is too old, lastAppliedIndex={}, writeLastLogIndex={}", this.applied.getIndex(), lastLog.getId().getIndex());
            return false;
        }
        if (firstLog.getId().getIndex() == this.lastLogIndex + 1) {
            this.lastLogIndex = lastLog.getId().getIndex();
        } else {
            //部分冲突，则删除本地冲突部分
            int conflictingIndex = 0;
            for (; conflictingIndex < entries.size(); conflictingIndex++) {
                if (unsafeGetTerm(entries.get(conflictingIndex).getId().getIndex()) != entries
                        .get(conflictingIndex).getId().getTerm()) {
                    break;
                }
            }
            if (conflictingIndex != entries.size()) {
                if (entries.get(conflictingIndex).getId().getIndex() <= this.lastLogIndex) {
                    unsafeTruncateSuffix(entries.get(conflictingIndex).getId().getIndex() - 1);
                }
                this.lastLogIndex = lastLog.getId().getIndex();
            }
            if (conflictingIndex > 0) {
                entries.subList(0, conflictingIndex).clear();
            }
        }
        return true;
    }

    public long getTerm(long index) {
        if (index == 0) {
            return 0;
        }
        this.lock.readLock().lock();
        try {
            if (index > this.lastLogIndex || index < this.firstLogIndex) {
                return 0;
            }
            final LogEntry entry = getLogEntryFromMemory(index);
            if (entry != null) {
                return entry.getId().getTerm();
            }
        } finally {
            this.lock.readLock().unlock();
        }
        LogEntry entry = logStorage.getEntry(index);
        if (entry != null) {
            return entry.getId().getTerm();
        }
        return 0;
    }

    public long getFirstLogIndex() {
        this.lock.readLock().lock();
        try {
            return firstLogIndex;
        } finally {
            this.lock.readLock().unlock();
        }
    }

    public long getLastLogIndex() {
        this.lock.readLock().lock();
        try {
            return this.lastLogIndex;
        } finally {
            this.lock.readLock().unlock();
        }
    }

    public LogId getLastLogId() {
        this.lock.readLock().lock();
        try {
            LogEntry logEntry = getLogEntry(this.lastLogIndex);
            return logEntry == null ? new LogId(0, 0) : logEntry.getId();
        } finally {
            this.lock.readLock().unlock();
        }
    }

    private long unsafeGetTerm(long index) {
        if (index == 0) {
            return 0;
        }
        if (index > this.lastLogIndex || index < this.firstLogIndex) {
            return 0;
        }
        LogEntry logEntry = getLogEntryFromMemory(index);
        if (logEntry != null) {
            return logEntry.getId().getTerm();
        }
        logEntry = logStorage.getEntry(index);
        if (logEntry != null) {
            return logEntry.getId().getTerm();
        }
        return 0;
    }

    public LogEntry getLogEntry(long index) {
        this.lock.readLock().lock();
        try {
            LogEntry logEntry = getLogEntryFromMemory(index);
            if (logEntry != null) {
                return logEntry;
            }
            return logStorage.getEntry(index);
        } finally {
            this.lock.readLock().unlock();
        }
    }

    private LogEntry getLogEntryFromMemory(long index) {
        this.lock.readLock().lock();
        try {
            if (this.logsInMemory.isEmpty()) {
                return null;
            }
            LogEntry logEntry = null;
            long firstIndex = logsInMemory.getFirst().getId().getIndex();
            long lastIndex = logsInMemory.getLast().getId().getIndex();
            if (index >= firstIndex && index <= lastIndex) {
                int i = 0;
                for (LogEntry log : logsInMemory) {
                    if (i == index - firstIndex) {
                        logEntry = log;
                        break;
                    }
                    i++;
                }
                return logEntry;
            }
            return null;
        } finally {
            this.lock.readLock().unlock();
        }
    }

    private void unsafeTruncateSuffix(long index) {
        this.logsInMemory.removeIf(entry -> entry.getId().getIndex() > index);
        this.logStorage.truncateSuffix(index);
    }

    public void setAppliedId(final LogId appliedId) {
        LogId clearId;
        this.lock.writeLock().lock();
        try {
            if (appliedId.compareTo(this.applied) < 0) {
                return;
            }
            this.applied = appliedId;
            clearId = this.diskId.compareTo(this.applied) <= 0 ? this.diskId : this.applied;
            if (clearId != null) {
                clearMemoryLogs(clearId);
            }
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    private void setDiskId(final LogId id) {
        if (id == null) {
            return;
        }
        LogId clearId;
        this.lock.writeLock().lock();
        try {
            if (id.compareTo(this.diskId) < 0) {
                return;
            }
            this.diskId = id;
            clearId = this.diskId.compareTo(this.applied) <= 0 ? this.diskId : this.applied;
            if (clearId != null) {
                clearMemoryLogs(clearId);
            }
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    private void clearMemoryLogs(final LogId id) {
        this.lock.writeLock().lock();
        try {
            this.logsInMemory.removeIf(entry -> entry.getId().compareTo(id) <= 0);
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    public long wait(long expectedLastLogIndex, Runnable callBack) {
        this.lock.writeLock().lock();
        try {
            if (expectedLastLogIndex != this.lastLogIndex) {
                executor.submit(callBack);
                return 0;
            }
            long waitId = nextWaitId.getAndIncrement();
            waitMap.put(waitId, callBack);
            return waitId;
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    public boolean removeWaiter(long waitId) {
        this.lock.writeLock().lock();
        try {
            return waitMap.remove(waitId) != null;
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    private boolean wakeupAllWaiter() {
        if (this.waitMap.isEmpty()) {
            return false;
        }
        ArrayList<Runnable> list = new ArrayList<>(waitMap.values());
        waitMap.clear();
        list.forEach(executor::submit);
        return true;
    }

    @Data
    private static class AppendLogEntriesEvent {
        private List<LogEntry> entries;
        private StableCallback callback;

        private void reset() {
            entries = null;
            callback = null;
        }
    }

    private class AppendEntriesHandler implements EventHandler<AppendLogEntriesEvent> {
        List<StableCallback> callbackList = new ArrayList<>();
        List<LogEntry> entries = new ArrayList<>();


        @Override
        public void onEvent(AppendLogEntriesEvent event, long sequence, boolean endOfBatch) throws Exception {
            entries.addAll(event.getEntries());
            if (event.getCallback() != null) {
                callbackList.add(event.getCallback());
            }
            if (endOfBatch || entries.size() >= RaftConf.getInstance().getBatchSize()) {
                LogId lastId = flush();
                setDiskId(lastId);
            }
        }

        private LogId flush() {
            if (entries.isEmpty()) {
                return null;
            }
            LogId lastId = entries.getLast().getId();
            logStorage.appendEntries(entries);
            for (StableCallback runnable : callbackList) {
                runnable.run(true);
                runnable.getEntries().clear();
            }
            callbackList.clear();
            entries.clear();
            return lastId;
        }
    }


    @Data
    public static abstract class StableCallback {
        protected long firstLogIndex = 0;
        protected List<LogEntry> entries;

        public StableCallback(List<LogEntry> entries) {
            this.entries = entries;
        }

        protected abstract void run(boolean success);

    }
}
