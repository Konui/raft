package cn.marci.raft.test.core.log;

import cn.marci.raft.core.log.LogEntry;
import cn.marci.raft.core.log.LogId;
import cn.marci.raft.core.log.LogStorage;
import cn.marci.raft.core.log.impl.RocksDBLogStorage;
import cn.marci.raft.utils.FilesUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class RocksDBLogStorageTest {

    private static final String TEST_DIR_PATH = "./tmp_rocksdb_test";

    private static LogStorage logStorage;

    @BeforeAll
    public static void init() throws IOException {
        FilesUtils.delete(TEST_DIR_PATH);
        logStorage = new RocksDBLogStorage(TEST_DIR_PATH);
    }

    @AfterAll
    public static void destroy() throws IOException {
        FilesUtils.delete(TEST_DIR_PATH);
    }

    @Test
    public void appendEntry() {
        List<LogEntry> list = new ArrayList<>(10);
        for (int i = 0; i<10; i++) {
            LogEntry logEntry = new LogEntry();
            logEntry.setId(new LogId(1, i));
            logEntry.setData(ByteBuffer.wrap(new byte[]{(byte) i}));
            list.add(logEntry);
            logStorage.appendEntry(logEntry);
        }
        for (int i = 0; i<10; i++) {
            LogEntry entry = logStorage.getEntry(i);
            assert entry != null;
            System.out.println(entry.toString());
        }
    }

    @Test
    public void truncateSuffix() {
        List<LogEntry> list = new ArrayList<>(10);
        for (int i = 0; i<10; i++) {
            LogEntry logEntry = new LogEntry();
            logEntry.setId(new LogId(1, i));
            logEntry.setData(ByteBuffer.wrap(new byte[]{(byte) i}));
            list.add(logEntry);
            logStorage.appendEntry(logEntry);
        }
        logStorage.truncateSuffix(5);
        for (int i = 0; i<10; i++) {
            LogEntry entry = logStorage.getEntry(i);
            System.out.println(entry);
        }
    }

    @Test
    public void truncatePrefix() {
        List<LogEntry> list = new ArrayList<>(10);
        for (int i = 0; i<10; i++) {
            LogEntry logEntry = new LogEntry();
            logEntry.setId(new LogId(1, i));
            logEntry.setData(ByteBuffer.wrap(new byte[]{(byte) i}));
            list.add(logEntry);
            logStorage.appendEntry(logEntry);
        }
        logStorage.truncatePrefix(5);
        for (int i = 0; i<10; i++) {
            LogEntry entry = logStorage.getEntry(i);
            System.out.println(entry);
        }
    }

    @Test
    public void getIndex() {
        long firstLogIndex = logStorage.getFirstLogIndex();
        long lastLogIndex = logStorage.getLastLogIndex();
        log.info("firstLogIndex: {}, lastLogIndex: {}", firstLogIndex, lastLogIndex);
    }
}
