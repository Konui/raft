package cn.marci.raft.core.node;

import cn.marci.raft.core.log.LogEntry;
import lombok.Data;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

@Data
public class Task {

    private long exceptedTerm = -1;

    private ByteBuffer data = LogEntry.EMPTY_DATA;

    private CompletableFuture<?> doneCf;


    public Task(ByteBuffer data, CompletableFuture<?> doneCf) {
        this(-1, data, doneCf);
    }

    public Task(long exceptedTerm, ByteBuffer data, CompletableFuture<?> doneCf) {
        this.exceptedTerm = exceptedTerm;
        this.data = data;
        this.doneCf = doneCf;
    }
}
