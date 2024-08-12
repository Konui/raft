package cn.marci.raft.core.node;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

@Data
@AllArgsConstructor
public class OperationMeta {

    private CompletableFuture<?> done;

    private ByteBuffer data;
}
