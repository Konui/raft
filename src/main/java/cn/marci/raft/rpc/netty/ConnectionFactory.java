package cn.marci.raft.rpc.netty;

import cn.marci.raft.common.Endpoint;
import cn.marci.raft.common.Lifecycle;
import cn.marci.raft.serializer.SerializerFactory;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.IdleStateHandler;

import java.net.InetSocketAddress;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class ConnectionFactory implements Lifecycle {

    private Bootstrap bootstrap;

    private final SerializerFactory serializerFactory;

    public ConnectionFactory(SerializerFactory serializerFactory) {
        this.serializerFactory = serializerFactory;
    }

    @Override
    public void start() {
        EventLoopGroup workGroup = new NioEventLoopGroup();

        bootstrap = new Bootstrap();
        bootstrap.group(workGroup);
        bootstrap.channel(NioSocketChannel.class);
        bootstrap.option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT);
        bootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline()
                        .addLast(new NettyRpcDecoder(serializerFactory.getInstance()))
                        .addLast(new NettyRpcEncoder(serializerFactory.getInstance()))
                        .addLast(new IdleStateHandler(60 * 1000, 0, 0, MILLISECONDS))
                        .addLast(new NettyClientHandler());
            }
        });
    }

    @Override
    public void stop() {
        Lifecycle.super.stop();
    }


    public Connection createConnection(Endpoint endpoint) {

        ChannelFuture future = bootstrap.connect(new InetSocketAddress(endpoint.getIp(), endpoint.getPort()));

        future.awaitUninterruptibly();
        //TODO 校验future完成状态
        return new Connection(endpoint, future.channel());
    }
}
