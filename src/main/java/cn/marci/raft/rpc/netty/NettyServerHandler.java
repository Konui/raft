package cn.marci.raft.rpc.netty;

import cn.marci.raft.rpc.RpcRequest;
import cn.marci.raft.rpc.RpcResponse;
import cn.marci.raft.rpc.UserProcessor;
import cn.marci.raft.utils.NetUtils;
import cn.marci.raft.utils.ThreadPoolUtils;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
@ChannelHandler.Sharable
public class NettyServerHandler extends ChannelInboundHandlerAdapter {

    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(NetUtils.getCpuCount(), NetUtils.getCpuCount() * 2, 1, TimeUnit.MINUTES, new ArrayBlockingQueue<>(200), ThreadPoolUtils.getThreadFactory(true, "NettyRpcExecutor"));

    private final Map<String, UserProcessor> processorMap;

    public NettyServerHandler(Map<String, UserProcessor> processorMap) {
        this.processorMap = processorMap;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        executor.execute(() -> {
            if (msg instanceof RpcRequest request) {
                if (log.isDebugEnabled()) {
                    log.debug("receive rpc [{}] request: {}", ctx.channel().remoteAddress(), request);
                }
                Object resp = process(request);
                ctx.channel().writeAndFlush(resp);
                if (log.isDebugEnabled()) {
                    log.debug("send rpc [{}] response: {}", ctx.channel().remoteAddress(), resp);
                }
            } else {
                log.warn("Unsupported message type: {}", msg.getClass().getName());
            }
        });
    }

    private Object process(RpcRequest request) {
        Long id = request.getId();
        UserProcessor userProcessor = processorMap.get(request.getInterest());
        if (userProcessor == null) {
            log.error("can't find [{}] UserProcessor", request.getInterest());
            return new RpcResponse(id, false, null, String.format("can't find [%s] UserProcessor", request.getInterest()), null);
        }
        try {
            Object data = userProcessor.handleRequest(request.getArg());
            return new RpcResponse(id, data);
        } catch (Exception e) {
            log.error("rpc process error", e);
            return new RpcResponse(id, false, null, e.getMessage(), e);
        }
    }
}
