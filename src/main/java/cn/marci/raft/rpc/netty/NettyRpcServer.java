package cn.marci.raft.rpc.netty;

import cn.marci.raft.rpc.RpcServer;
import cn.marci.raft.serializer.SerializerSingleFactory;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NettyRpcServer extends RpcServer {

    private final SerializerSingleFactory serializerFactory;

    private Channel channel;

    public NettyRpcServer(int port, SerializerSingleFactory serializerFactory) {
        super(port);
        this.serializerFactory = serializerFactory;
    }

    @Override
    public void close() {
        channel.close();
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
                                    .addLast(new NettyServerHandler(services));
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
