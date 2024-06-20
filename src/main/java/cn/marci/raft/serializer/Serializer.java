package cn.marci.raft.serializer;

import java.io.IOException;

public interface Serializer {

    byte[] serialize(Object object) throws IOException;

    <T> T deserialize(byte[] bytes) throws IOException;

}
