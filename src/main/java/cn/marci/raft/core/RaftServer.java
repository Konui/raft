package cn.marci.raft.core;

import cn.marci.raft.core.conf.RaftConf;
import cn.marci.raft.core.node.Node;
import cn.marci.raft.core.node.NodeImpl;
import cn.marci.raft.core.rpc.RpcService;
import cn.marci.raft.core.rpc.impl.RpcServiceImpl;
import cn.marci.raft.rpc.RpcConsumerFactory;
import cn.marci.raft.rpc.RpcServer;
import cn.marci.raft.rpc.netty.ConnectionFactory;
import cn.marci.raft.rpc.netty.ConnectionManager;
import cn.marci.raft.rpc.netty.NettyRpcClient;
import cn.marci.raft.rpc.netty.NettyRpcServer;
import cn.marci.raft.serializer.SerializerFactory;

import java.util.concurrent.locks.LockSupport;

public class RaftServer {

    public static void main(String[] args) {
        RaftConf conf = new RaftConf();

        //rpc client端
        SerializerFactory serializerFactory = new SerializerFactory();
        ConnectionFactory connectionFactory = new ConnectionFactory(serializerFactory);
        connectionFactory.start();
        NettyRpcClient nettyRpcClient = new NettyRpcClient(new ConnectionManager(connectionFactory));

        RpcConsumerFactory rpcConsumerFactory = new RpcConsumerFactory(nettyRpcClient);
        RpcService rpcService = rpcConsumerFactory.getClient(RpcService.class);

        //node
        Node node = new NodeImpl(conf, rpcService);

        //rpc server端
        RpcServer rpcServer = new NettyRpcServer(conf.getRpcServerPort(), serializerFactory);
        rpcServer.registerService(RpcService.class, new RpcServiceImpl(node));
        rpcServer.start();

        LockSupport.park();
    }


}
