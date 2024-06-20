package cn.marci.raft.rpc.netty;

import cn.marci.raft.rpc.RpcProcessor;
import cn.marci.raft.rpc.RpcServer;
import cn.marci.raft.serializer.SerializerFactory;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class NettyRpcServer implements RpcServer {

    private final int port;

    private final SerializerFactory serializerFactory;

    private final List<RpcProcessor> processors;

    private final AtomicBoolean running = new AtomicBoolean(false);

    private Channel channel;

    public NettyRpcServer(int port, SerializerFactory serializerFactory, List<RpcProcessor> processors) {
        this.port = port;
        this.serializerFactory = serializerFactory;
        this.processors = processors;
    }

    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            Thread thread = new Thread(this::runServer);
            thread.setDaemon(true);
            thread.setName("NettyRpcServer");
            thread.start();
            log.info("rpc server stated on port {}", port);
        }
    }

    @Override
    public void stop() {
        if (running.compareAndSet(true, false)) {
            channel.close();
        }
    }

    public void runServer() {
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap.group(bossGroup, workGroup);
            serverBootstrap.channel(NioServerSocketChannel.class);
            serverBootstrap.option(ChannelOption.SO_REUSEADDR, Boolean.TRUE)
                    .childOption(ChannelOption.TCP_NODELAY, Boolean.TRUE)
                    .childOption(ChannelOption.SO_KEEPALIVE, Boolean.TRUE)
                    .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel socketChannel) throws Exception {
                            socketChannel.pipeline()
                                    .addLast(new NettyRpcDecoder(serializerFactory.getInstance()))
                                    .addLast(new NettyRpcEncoder(serializerFactory.getInstance()))
                                    .addLast(new NettyServerHandler(processors));
                        }
                    });

            ChannelFuture future = serverBootstrap.bind(port).sync();
            channel = future.channel();
            future.channel().closeFuture().sync();
        } catch (Exception e) {
            log.error("Netty server error", e);
        } finally {
            bossGroup.shutdownGracefully();
            workGroup.shutdownGracefully();
        }
    }
}
