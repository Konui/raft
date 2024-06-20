package cn.marci.raft.rpc.netty;

import cn.marci.raft.common.Endpoint;
import cn.marci.raft.rpc.RpcClient;
import cn.marci.raft.rpc.RpcException;
import cn.marci.raft.rpc.RpcRequest;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
public class NettyRpcClient implements RpcClient {

    private final ConnectionManager connectionManager;

    public NettyRpcClient(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public Object invokeSync(Endpoint endpoint, long timeout, String signature, Object... args) {
        CompletableFuture future = invoke(endpoint, signature, args);
        try {
            return future.get(timeout, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.error("RPC invoke error: {}", e.getMessage());
            throw new RpcException(e);
        }
    }

    @Override
    public Object invokeAsync(Endpoint endpoint, String signature, Object... args) {
        return invoke(endpoint, signature, args);
    }

    private CompletableFuture invoke(Endpoint endpoint, String signature, Object... args) {
        Connection connection = connectionManager.getOrCreate(endpoint);

        RpcRequest request = new RpcRequest(signature, args);

        CompletableFuture completableFuture = connection.addInvokeFuture(request.getId(), new CompletableFuture<>());

        connection.getChannel().writeAndFlush(request).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (!future.isSuccess()) {
                    connection.remove(request.getId());
                }
            }
        });
        return completableFuture;
    }
}
