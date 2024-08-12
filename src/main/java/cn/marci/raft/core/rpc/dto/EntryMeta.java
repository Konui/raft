package cn.marci.raft.core.rpc.dto;

import cn.marci.raft.core.log.LogEntry;
import cn.marci.raft.core.log.LogId;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;
import java.nio.ByteBuffer;

@Data
@AllArgsConstructor
public class EntryMeta implements Serializable {
    private LogId logId;

    private byte[] bytes;


    public static EntryMeta toMeta(LogEntry logEntry) {
        return new EntryMeta(logEntry.getId(), logEntry.getData().array());
    }

    public LogEntry toLogEntry() {
        LogEntry logEntry = new LogEntry();
        logEntry.setId(this.logId);
        logEntry.setData(ByteBuffer.wrap(bytes));
        return logEntry;
    }
}
