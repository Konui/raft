package cn.marci.raft.test.rpc;

import cn.marci.raft.common.Endpoint;
import cn.marci.raft.rpc.RpcProcessor;
import cn.marci.raft.rpc.netty.ConnectionFactory;
import cn.marci.raft.rpc.netty.ConnectionManager;
import cn.marci.raft.rpc.netty.NettyRpcClient;
import cn.marci.raft.rpc.netty.NettyRpcServer;
import cn.marci.raft.serializer.SerializerFactory;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.locks.LockSupport;

@Slf4j
public class RpcTest {

    public class TestProcessor implements RpcProcessor {

        @Override
        public String signature() {
            return "raft";
        }

        @Override
        public Object process(Object... args) {
            log.info("receive args:{}", args);
            return "abc";
        }
    }


    @Test
    public void startRpcServer() {
        NettyRpcServer nettyRpcServer = new NettyRpcServer(8091, new SerializerFactory(), List.of(new TestProcessor()));
        nettyRpcServer.start();
        LockSupport.park();
    }


    @Test
    public void startRpcClientAndTest() {
        ConnectionFactory connectionFactory = new ConnectionFactory(new SerializerFactory());
        connectionFactory.start();
        NettyRpcClient nettyRpcClient = new NettyRpcClient(new ConnectionManager(connectionFactory));

        Object o = nettyRpcClient.invokeSync(new Endpoint("127.0.0.1", 8091), 10000, "raft", "hellow");
        System.out.println(o);
    }
}
