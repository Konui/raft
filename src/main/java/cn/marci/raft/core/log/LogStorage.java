package cn.marci.raft.core.log;

import cn.marci.raft.common.Lifecycle;

import java.util.List;

public interface LogStorage extends Lifecycle {

    long getFirstLogIndex();

    long getLastLogIndex();

    LogEntry getEntry(long index);

    void appendEntry(LogEntry entry);

    void appendEntries(List<LogEntry> entries);

    /**
     * 从存储的头部删除日志，[first_log_index，first_index_kept）将被丢弃
     */
    void truncatePrefix(long firstIndexKept);

    /**
     * 从存储的尾部删除未提交的日志，（last_index_kept、last_log_index] 将被丢弃
     */
    void truncateSuffix(long lastIndexKept);

    void reset(long nextLogIndex);
}
