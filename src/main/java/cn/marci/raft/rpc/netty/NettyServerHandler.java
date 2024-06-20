package cn.marci.raft.rpc.netty;

import cn.marci.raft.rpc.RpcProcessor;
import cn.marci.raft.rpc.RpcRequest;
import cn.marci.raft.rpc.RpcResponse;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@ChannelHandler.Sharable
public class NettyServerHandler extends ChannelInboundHandlerAdapter {
    private Map<String, RpcProcessor> processorMap;

    public NettyServerHandler(List<RpcProcessor> processors) {
        if (processors == null || processors.isEmpty()) {
            throw new IllegalArgumentException("processors cannot be null or empty");
        }
        processorMap = processors.stream()
                .collect(Collectors.toMap(RpcProcessor::signature, processor -> processor));
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
        RpcProcessor rpcProcessor = processorMap.get(request.getSignature());
        if (rpcProcessor == null) {
            return new RpcResponse(id, false, null, String.format("can't find [%s] signature processor", request.getSignature()), null);
        }
        try {
            Object data = rpcProcessor.process(request.getArgs());
            return new RpcResponse(id, data);
        } catch (Exception e) {
            log.error("rpc process error", e);
            return new RpcResponse(id, false, null, e.getMessage(), e);
        }
    }
}
