package cn.marci.raft.test.rpc;

import cn.marci.raft.common.Endpoint;
import cn.marci.raft.rpc.RpcConsumerFactory;
import cn.marci.raft.rpc.netty.ConnectionFactory;
import cn.marci.raft.rpc.netty.ConnectionManager;
import cn.marci.raft.rpc.netty.NettyRpcClient;
import cn.marci.raft.rpc.netty.NettyRpcServer;
import cn.marci.raft.serializer.SerializerFactory;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.concurrent.locks.LockSupport;

@Slf4j
public class RpcTest {

    public static interface TestInterface {
        String test(Endpoint endpoint, int cnt);
    }

    public static class TestImpl implements TestInterface {
        @Override
        public String test(Endpoint endpoint, int cnt) {
            return "ooook!".repeat(cnt);
        }
    }

    @Test
    public void startRpcServer() {
        NettyRpcServer nettyRpcServer = new NettyRpcServer(8091, new SerializerFactory());
        nettyRpcServer.registerService(TestInterface.class, new TestImpl());
        nettyRpcServer.start();
        LockSupport.park();
    }


    @Test
    public void startRpcClientAndTest() {
        ConnectionFactory connectionFactory = new ConnectionFactory(new SerializerFactory());
        connectionFactory.start();
        NettyRpcClient nettyRpcClient = new NettyRpcClient(new ConnectionManager(connectionFactory));

        RpcConsumerFactory rpcConsumerFactory = new RpcConsumerFactory(nettyRpcClient);
        TestInterface client = rpcConsumerFactory.getClient(TestInterface.class);

        String test = client.test(new Endpoint("127.0.0.1", 8091), 10);
        System.out.println(test);

    }
}
