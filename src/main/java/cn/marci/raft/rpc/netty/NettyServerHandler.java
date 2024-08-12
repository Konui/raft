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
        if (msg instanceof RpcRequest request) {
            if (log.isDebugEnabled()) {
                log.debug("receive rpc [{}] request: {}", ctx.channel().remoteAddress(), request);
            }
            UserProcessor userProcessor = processorMap.get(request.getInterest());
            if (userProcessor == null) {
                Long id = request.getId();
                log.error("can't find [{}] UserProcessor", request.getInterest());
                RpcResponse rpcResponse = new RpcResponse(id, false, null, String.format("can't find [%s] UserProcessor", request.getInterest()), null);
                sendResponse(ctx, rpcResponse);
            }
            switch (userProcessor.handlerType()) {
                case ASYNC -> {
                    //异步并发提交到线程池
                    executor.execute(() -> {
                        Object resp = process(request, userProcessor);
                        sendResponse(ctx, resp);
                    });
                }
                case SYNC -> {
                    //单线程处理, 处理逻辑耗时不能过大, 否则会阻塞io线程
                    Object resp = process(request, userProcessor);
                    sendResponse(ctx, resp);
                }
                case ASYNC_SEND_RESP_BY_USER -> {
                    executor.execute(() -> {
                        processAsync(request, userProcessor, ctx);
                    });
                }
            }
        } else {
            log.warn("Unsupported message type: {}", msg.getClass().getName());
        }
    }

    private Object process(RpcRequest request, UserProcessor userProcessor) {
        Long id = request.getId();
        try {
            Object data = userProcessor.handleRequest(request.getArg());
            return new RpcResponse(id, data);
        } catch (Exception e) {
            log.error("rpc process error", e);
            return new RpcResponse(id, false, null, e.getMessage(), e);
        }
    }

    private void processAsync(RpcRequest request, UserProcessor userProcessor, ChannelHandlerContext ctx) {
        Long id = request.getId();
        try {
            userProcessor.handleRequestAsync(request.getArg(), resp -> {
                if (resp instanceof RpcResponse response) {
                    response.setId(id);
                    //用于发送失败结果
                    sendResponse(ctx, response);
                } else {
                    sendResponse(ctx, new RpcResponse(id, resp));
                }
            });
        } catch (Exception e) {
            log.error("rpc process error", e);
            sendResponse(ctx, new RpcResponse(id, false, null, e.getMessage(), e));
        }
    }

    private void sendResponse(ChannelHandlerContext ctx, Object resp) {
        ctx.channel().writeAndFlush(resp);
        if (log.isDebugEnabled()) {
            log.debug("send rpc [{}] response: {}", ctx.channel().remoteAddress(), resp);
        }
    }
}
