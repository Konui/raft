package cn.marci.raft.core.log;

import lombok.Data;

import java.io.Serializable;
import java.nio.ByteBuffer;

@Data
public class LogEntry implements Serializable {

    public static final ByteBuffer EMPTY_DATA = ByteBuffer.wrap(new byte[0]);

    private LogId id = new LogId();

    private LogType type = LogType.UNKNOWN;

    private ByteBuffer data = EMPTY_DATA;

    public static long deserializeIndex(byte[] bytes) {
        return ByteBuffer.wrap(bytes).getLong();
    }

    public static byte[] serializeIndex(long index) {
        return ByteBuffer.allocate(8).putLong(index).array();
    }
}
