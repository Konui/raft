package cn.marci.raft.core.log.impl;

import cn.marci.raft.common.Lifecycle;
import cn.marci.raft.core.log.LogEntry;
import cn.marci.raft.core.log.LogStorage;
import lombok.extern.slf4j.Slf4j;
import org.rocksdb.*;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Slf4j
public class RocksDBLogStorage implements LogStorage, Lifecycle {

    private ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private final String filePath;

    private final Options options;

    private final RocksDB db;

    private ColumnFamilyHandle defaultColHandle;

    static {
        RocksDB.loadLibrary();
    }

    public RocksDBLogStorage(String filePath) {
        this.filePath = filePath;
        try {
            Files.createDirectories(Paths.get(filePath));
            options = new Options().setCreateIfMissing(true);
            db = RocksDB.open(options, filePath);
            defaultColHandle = db.getDefaultColumnFamily();
        } catch (Exception e) {
            throw new IllegalStateException("create log dir failed, path: " + filePath, e);
        }
    }

    @Override
    public void stop() {
        db.close();
        options.close();
    }

    @Override
    public long getFirstLogIndex() {
        this.lock.readLock().lock();
        try (RocksIterator iterator = db.newIterator()) {
            iterator.seekToFirst();
            if (iterator.isValid()) {
                return LogEntry.deserializeIndex(iterator.key());
            }
        } catch (Exception e) {
            log.error("get first log index failed, path: {}", filePath, e);
        } finally {
            this.lock.readLock().unlock();
        }
        return 1;
    }

    @Override
    public long getLastLogIndex() {
        this.lock.readLock().lock();
        try (RocksIterator iterator = db.newIterator()) {
            iterator.seekToLast();
            if (iterator.isValid()) {
                return LogEntry.deserializeIndex(iterator.key());
            }
        } catch (Exception e) {
            log.error("get last log index failed, path: {}", filePath, e);
        } finally {
            this.lock.readLock().unlock();
        }
        return 0;
    }

    @Override
    public LogEntry getEntry(long index) {
        this.lock.readLock().lock();
        try {
            byte[] bytes = db.get(LogEntry.serializeIndex(index));
            if (bytes == null) {
                return null;
            }
            return LogEntry.deserialize(bytes);
        } catch (Exception e) {
            log.error("get log entry failed, path: {}, index: {}", filePath, index, e);
        } finally {
            this.lock.readLock().unlock();
        }
        return null;
    }

    @Override
    public void appendEntry(LogEntry entry) {
        if (entry == null) {
            return;
        }
        this.lock.writeLock().lock();
        try {
            db.put(LogEntry.serializeIndex(entry.getId().getIndex()), LogEntry.serialize(entry));
        } catch (Exception e) {
            log.error("append log entry failed, path: {}", filePath, e);
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    @Override
    public void appendEntries(List<LogEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        this.lock.writeLock().lock();
        try {
            WriteOptions writeOptions = new WriteOptions();
            WriteBatch writeBatch = new WriteBatch();
            for (LogEntry entry : entries) {
                writeBatch.put(LogEntry.serializeIndex(entry.getId().getIndex()), LogEntry.serialize(entry));
            }
            db.write(writeOptions, writeBatch);
            if (log.isDebugEnabled()) {
                log.debug("append log entries, path: {}, entries: {}", filePath, entries);
            }
        } catch (Exception e) {
            log.error("append log entry failed, path: {}", filePath, e);
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    @Override
    public void truncatePrefix(long firstIndexKept) {
        this.lock.writeLock().lock();
        try {
            long firstLogIndex = getFirstLogIndex();
            byte[] startKey = LogEntry.serializeIndex(firstLogIndex);
            byte[] endKey = LogEntry.serializeIndex(firstIndexKept);
            db.deleteRange(defaultColHandle, startKey, endKey);
            db.deleteFilesInRanges(defaultColHandle, Arrays.asList(startKey, endKey), false);
        } catch (Exception e) {
            log.error("truncate prefix failed, path: {}", filePath, e);
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    @Override
    public void truncateSuffix(long lastIndexKept) {
        this.lock.writeLock().lock();
        try {
            long lastLogIndex = getLastLogIndex();
            byte[] startKey = LogEntry.serializeIndex(lastIndexKept + 1);
            byte[] endKey = LogEntry.serializeIndex(lastLogIndex + 1);
            db.deleteRange(defaultColHandle, startKey, endKey);
            db.deleteFilesInRanges(defaultColHandle, Arrays.asList(startKey, endKey), false);
        } catch (Exception e) {
            log.error("truncate suffix failed, path: {}", filePath, e);
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    @Override
    public void reset(long nextLogIndex) {
        throw new IllegalCallerException("reset not supported");
//        if (nextLogIndex <= 0) {
//            throw new IllegalArgumentException("Invalid next log index.");
//        }
//        this.lock.writeLock().lock();
//        try (Options opt = new Options()) {
//            LogEntry entry = getEntry(nextLogIndex);
//
//            RocksDB.destroyDB(filePath, opt);
//
//        } catch (Exception e) {
//
//        } finally {
//            this.lock.writeLock().unlock();
//        }
    }

}
