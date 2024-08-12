package cn.marci.raft.rpc;

import cn.marci.raft.common.Endpoint;
import cn.marci.raft.common.Lifecycle;
import cn.marci.raft.rpc.netty.*;
import cn.marci.raft.serializer.SerializerSingleFactory;

public class RpcFactory implements Lifecycle {

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

    public Connection connect(Endpoint endpoint) {
        return connectionManager.getOrCreate(endpoint);
    }

    public static RpcFactory getInstance() { return RpcFactoryHolder.INSTANCE; }

    private static class RpcFactoryHolder {
        private static final RpcFactory INSTANCE = new RpcFactory();
    }

    @Override
    public void stop() {
        if (rpcServer != null) {
            rpcServer.stop();
        }
        if (connectionManager != null) {
            connectionManager.stop();
        }
    }

}
