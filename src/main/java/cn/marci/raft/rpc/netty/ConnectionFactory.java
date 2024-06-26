package cn.marci.raft.rpc.netty;

import cn.marci.raft.common.Endpoint;
import cn.marci.raft.common.Lifecycle;
import cn.marci.raft.rpc.RpcException;
import cn.marci.raft.serializer.SerializerSingleFactory;
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
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

@Slf4j
public class ConnectionFactory implements Lifecycle {

    private Bootstrap bootstrap;

    private final SerializerSingleFactory serializerFactory;

    public ConnectionFactory(SerializerSingleFactory serializerFactory) {
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
        if (!future.isDone()) {
            log.error("Create connection timeout, endpoint:{}", endpoint);
            throw new RpcException("Create connection timeout");
        }
        if (future.isCancelled()) {
            log.error("Create connection cancelled by user, endpoint:{}", endpoint);
            throw new RpcException("Create connection cancelled by user");
        }
        if (!future.isSuccess()) {
            log.error("Create connection error, endpoint:{}", endpoint);
            throw new RpcException("Create connection error", future.cause());
        }
        return new Connection(endpoint, future.channel());
    }
}
