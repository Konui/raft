package cn.marci.raft.rpc.netty;

import cn.marci.raft.rpc.RpcException;
import cn.marci.raft.rpc.RpcResponse;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Slf4j
@ChannelHandler.Sharable
public class NettyClientHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof RpcResponse response) {
            if (log.isDebugEnabled()) {
                log.info("receive rpc response, resp:{}", response);
            }
            Connection connection = ctx.channel().attr(Connection.CONNECTION_KEY).get();
            CompletableFuture future = connection.remove(response.getId());
            if (future == null) {
                log.warn("can't find invoke future, id: {}", response.getId());
                return;
            }
            if (response.isSuccess()) {
                future.complete(response.getData());
            } else {
                future.completeExceptionally(Optional.of(response.getThrowable())
                        .orElseGet(() -> new RpcException("server error, msg: " + response.getMsg())));
                log.error("rpc invoke error in server, error msg:{}", response.getMsg(), response.getThrowable());
            }
        } else {
            log.warn("unknown netty msg type, {}", msg);
            super.channelRead(ctx, msg);
        }
    }
}
