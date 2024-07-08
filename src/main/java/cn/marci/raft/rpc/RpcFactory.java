package cn.marci.raft.rpc;

import cn.marci.raft.common.Endpoint;
import cn.marci.raft.core.rpc.RpcService;
import cn.marci.raft.core.rpc.impl.RpcServiceImpl;
import cn.marci.raft.rpc.netty.ConnectionFactory;
import cn.marci.raft.rpc.netty.ConnectionManager;
import cn.marci.raft.rpc.netty.NettyRpcClient;
import cn.marci.raft.rpc.netty.NettyRpcServer;
import cn.marci.raft.serializer.SerializerSingleFactory;

public class RpcFactory {

    private final SerializerSingleFactory serializerFactory = new SerializerSingleFactory();

    /**
     * server
     */
    private RpcServer rpcServer;

    /**
     * client
     */
    private ConnectionManager connectionManager;

    private NettyRpcClient nettyRpcClient;

    public RpcClient initClient() {
        //rpc client端
        ConnectionFactory connectionFactory = new ConnectionFactory(serializerFactory);
        connectionFactory.start();
        connectionManager = new ConnectionManager(connectionFactory);
        nettyRpcClient = new NettyRpcClient(connectionManager);
        return nettyRpcClient;
    }

    public void initServer(int port) {
        //rpc server端
        rpcServer = new NettyRpcServer(port, serializerFactory);
        rpcServer.start();
    }

    public <T> void registerUserProcessor(UserProcessor userProcessor) {
        rpcServer.registerUserProcessor(userProcessor);
    }

    public void connect(Endpoint endpoint) {
        connectionManager.getOrCreate(endpoint);
    }

    public static RpcFactory getInstance() { return RpcFactoryHolder.INSTANCE; }

    private static class RpcFactoryHolder {
        private static final RpcFactory INSTANCE = new RpcFactory();
    }

}
