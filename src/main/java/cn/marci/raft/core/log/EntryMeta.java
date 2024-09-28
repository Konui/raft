package cn.marci.raft.core.log;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;
import java.nio.ByteBuffer;

@Data
@AllArgsConstructor
public class EntryMeta implements Serializable {
    private LogId logId;

    private LogType type;

    private byte[] bytes;

    public static EntryMeta toMeta(LogEntry logEntry) {
        return new EntryMeta(logEntry.getId(), logEntry.getType(), logEntry.getData().array());
    }

    public LogEntry toLogEntry() {
        LogEntry logEntry = new LogEntry();
        logEntry.setId(this.logId);
        logEntry.setData(ByteBuffer.wrap(this.bytes));
        logEntry.setType(this.type);
        return logEntry;
    }

    public static byte[] serializeForLogEntry(LogEntry logEntry) {
        if (logEntry == null) {
            return new byte[]{};
        }
        logEntry.getData().rewind();
        ByteBuffer bf = ByteBuffer.allocate(20 + logEntry.getData().limit());
        bf.putLong(logEntry.getId().getTerm());
        bf.putLong(logEntry.getId().getIndex());
        bf.putInt(logEntry.getType() == null ? 0 : logEntry.getType().ordinal());
        bf.put(logEntry.getData());
        logEntry.getData().rewind();
        return bf.array();
    }

    public static LogEntry deserializeToLogEntry(byte[] bytes) {
        if (bytes.length < 20) {
            throw new IllegalArgumentException("log entry bytes length must >= 16");
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);

        LogEntry entry = new LogEntry();
        entry.setId(new LogId(buffer.getLong(), buffer.getLong()));
        entry.setType(LogType.values()[buffer.getInt()]);
        ByteBuffer data = ByteBuffer.allocate(buffer.remaining());
        data.put(buffer);
        entry.setData(data);
        data.flip();
        return entry;
    }
}
