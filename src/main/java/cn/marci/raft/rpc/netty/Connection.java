package cn.marci.raft.rpc.netty;

import cn.marci.raft.common.Endpoint;
import cn.marci.raft.common.Lifecycle;
import cn.marci.raft.rpc.RpcException;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Getter
@Slf4j
public class Connection implements Lifecycle {

    public static final AttributeKey<Connection> CONNECTION_KEY = AttributeKey.valueOf("connection");

    private Endpoint endpoint;

    private Channel channel;

    private ConcurrentHashMap<Long, CompletableFuture> invokeFutureMap = new ConcurrentHashMap<>();

    public Connection(Endpoint endpoint, Channel channel) {
        this.endpoint = endpoint;
        this.channel = channel;
        this.channel.attr(CONNECTION_KEY).set(this);
    }

    public CompletableFuture addInvokeFuture(Long id, CompletableFuture future) {
        CompletableFuture f = this.invokeFutureMap.putIfAbsent(id, future);
        if (f != null) {
            log.warn("id:{} already exists", id);
            throw new RpcException("id already exists");
        }
        return future;
    }

    public CompletableFuture remove(Long id) {
        return invokeFutureMap.remove(id);
    }

    @Override
    public void stop() {
        channel.close();
        if (!invokeFutureMap.isEmpty()) {
            invokeFutureMap.values().forEach(f -> f.completeExceptionally(new RpcException("connection closed")));
        }
    }
}
