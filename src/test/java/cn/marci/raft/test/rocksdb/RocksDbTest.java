package cn.marci.raft.test.rocksdb;

import cn.marci.raft.core.log.EntryMeta;
import cn.marci.raft.core.log.LogEntry;
import cn.marci.raft.serializer.SerializerSingleFactory;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksIterator;

import java.nio.ByteBuffer;

public class RocksDbTest {

    static {
        RocksDB.loadLibrary();
    }

    public static void main(String[] args) throws Exception {
        String filePath = "./log_entry/count_test/127.0.0.1:8081";
        Options options = new Options().setCreateIfMissing(true);
        RocksDB db = RocksDB.open(options, filePath);

        RocksIterator iterator = db.newIterator();
        iterator.seekToFirst();
        while (iterator.isValid()) {
            iterator.key();
            long index = ByteBuffer.wrap(iterator.key()).getLong();
            LogEntry entry = EntryMeta.deserializeToLogEntry(iterator.value());
            System.out.printf("index:%d, LogId(term=%d, index=%d), data:%s%n", index, entry.getId().getTerm(), entry.getId().getIndex(), SerializerSingleFactory.getInstance().deserialize(entry.getData().array()).toString());
            iterator.next();
        }
    }

}
