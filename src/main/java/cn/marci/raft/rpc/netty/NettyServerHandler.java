package cn.marci.raft.rpc.netty;

import cn.marci.raft.rpc.RpcRequest;
import cn.marci.raft.rpc.RpcResponse;
import cn.marci.raft.rpc.RpcServer;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
@ChannelHandler.Sharable
public class NettyServerHandler extends ChannelInboundHandlerAdapter {
    private final Map<String, RpcServer.MethodMetadata> processorMap;

    public NettyServerHandler(Map<String, RpcServer.MethodMetadata> processorMap) {
        this.processorMap = processorMap;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof RpcRequest request) {
            if (log.isDebugEnabled()) {
                log.info("receive rpc request: {}", request);
            }
            Object resp = process(request);
            if (log.isDebugEnabled()) {
                log.info("send rpc response: {}", resp);
            }
            ctx.channel().writeAndFlush(resp);
        } else {
            log.warn("Unsupported message type: {}", msg.getClass().getName());
        }
    }

    private Object process(RpcRequest request) {
        Long id = request.getId();
        RpcServer.MethodMetadata methodMetadata = processorMap.get(request.getSignature());
        if (methodMetadata == null) {
            log.error("can't find [{}] signature processor", request.getSignature());
            return new RpcResponse(id, false, null, String.format("can't find [%s] signature processor", request.getSignature()), null);
        }
        try {
            Object data = methodMetadata.invoke(request.getArgs());
            return new RpcResponse(id, data);
        } catch (Exception e) {
            log.error("rpc process error", e);
            return new RpcResponse(id, false, null, e.getMessage(), e);
        }
    }
}
