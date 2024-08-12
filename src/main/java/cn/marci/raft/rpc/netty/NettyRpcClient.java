package cn.marci.raft.rpc.netty;

import cn.marci.raft.common.Endpoint;
import cn.marci.raft.rpc.RpcClient;
import cn.marci.raft.rpc.RpcException;
import cn.marci.raft.rpc.RpcRequest;
import cn.marci.raft.utils.ThreadPoolUtils;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.util.HashedWheelTimer;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
public class NettyRpcClient implements RpcClient {
    private static final Integer MAX_TIMEOUT = 1; // 1min
    private static final HashedWheelTimer TIMER = new HashedWheelTimer(ThreadPoolUtils.getThreadFactory(true, "NettyRpcTimeoutTimer"), 1, TimeUnit.SECONDS, 60);

    private final ConnectionManager connectionManager;

    public NettyRpcClient(ConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public Object invokeSync(Endpoint endpoint, long timeout, Object arg, String interest) {
        long startTime = System.currentTimeMillis();
        CompletableFuture future = invoke(endpoint, interest, arg);
        try {
            return future.get(timeout, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.error("RPC invoke interest:{}, error: {}", interest, e.getMessage());
            throw new RpcException(e);
        } finally {
            long cost = System.currentTimeMillis() - startTime;
            if (cost > 100 || log.isDebugEnabled()) {
                log.warn("RPC invoke cost: {}ms", cost);
            }
        }
    }

    @Override
    public Object invokeSync(Endpoint endpoint, long timeout, Object arg) {
        if (arg == null) {
            throw new IllegalArgumentException("arg is null, please use interest to invoke");
        }
        return invokeSync(endpoint, timeout, arg, arg.getClass().getName());
    }

    @Override
    public CompletableFuture invokeAsync(Endpoint endpoint, Object arg) {
        if (arg == null) {
            throw new IllegalArgumentException("arg is null, please use interest to invoke");
        }
        return invokeAsync(endpoint, arg, arg.getClass().getName());
    }

    @Override
    public CompletableFuture invokeAsync(Endpoint endpoint, Object arg, String interest) {
        return invoke(endpoint, interest, arg);
    }

    private CompletableFuture invoke(Endpoint endpoint, String interest, Object arg) {
        Connection connection = connectionManager.getOrCreate(endpoint);

        RpcRequest request = new RpcRequest(arg, interest);

        CompletableFuture completableFuture = connection.addInvokeFuture(request.getId(), new CompletableFuture<>());

        connection.getChannel().writeAndFlush(request).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (!future.isSuccess()) {
                    connection.remove(request.getId());
                }
            }
        });

        TIMER.newTimeout(timeout -> {
            connection.remove(request.getId());
        }, MAX_TIMEOUT, TimeUnit.MINUTES);

        if (log.isDebugEnabled()) {
            log.debug("RPC invoke{} interest:{}, arg:{}", endpoint, interest, arg);
        }
        return completableFuture;
    }
}
