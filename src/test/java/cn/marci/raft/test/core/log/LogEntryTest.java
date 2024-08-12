package cn.marci.raft.test.core.log;

import cn.marci.raft.core.log.LogEntry;
import cn.marci.raft.core.log.LogId;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

public class LogEntryTest {

    @Test
    public void test() {
        LogEntry logEntry = new LogEntry();
        logEntry.setId(new LogId(3,3));
        logEntry.setData(ByteBuffer.wrap(new byte[]{0,0,0,0,0,0,0,1}));

        byte[] bytes = LogEntry.serialize(logEntry);
        LogEntry entry = LogEntry.deserialize(bytes);

        assert entry.getId().equals(logEntry.getId());
        assert entry.getData().equals(logEntry.getData());
    }


}
