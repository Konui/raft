package cn.marci.raft.core.log;

import lombok.Data;

import java.io.Serializable;
import java.nio.ByteBuffer;

@Data
public class LogEntry implements Serializable {

    public static final ByteBuffer EMPTY_DATA = ByteBuffer.wrap(new byte[0]);

    private LogId id = new LogId();

    private ByteBuffer data = EMPTY_DATA;


    public static LogEntry deserialize(byte[] bytes) {
        if (bytes.length < 16) {
            throw new IllegalArgumentException("log entry bytes length must >= 16");
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);

        LogEntry entry = new LogEntry();
        entry.setId(new LogId(buffer.getLong(), buffer.getLong()));

        ByteBuffer data = ByteBuffer.allocate(buffer.remaining());
        data.put(buffer);
        entry.setData(data);
        data.flip();
        return entry;
    }

    public static byte[] serialize(LogEntry entry) {
        if (entry == null) {
            return new byte[]{};
        }
        entry.getData().rewind();
        ByteBuffer bf = ByteBuffer.allocate(16 + entry.getData().limit());
        bf.putLong(entry.getId().getTerm());
        bf.putLong(entry.getId().getIndex());
        bf.put(entry.getData());
        entry.getData().rewind();
        return bf.array();
    }

    public static long deserializeIndex(byte[] bytes) {
        return ByteBuffer.wrap(bytes).getLong();
    }

    public static byte[] serializeIndex(long index) {
        return ByteBuffer.allocate(8).putLong(index).array();
    }
}
