package cn.marci.raft.test.rpc;

import cn.marci.raft.common.Endpoint;
import cn.marci.raft.rpc.UserProcessor;
import cn.marci.raft.rpc.netty.ConnectionFactory;
import cn.marci.raft.rpc.netty.ConnectionManager;
import cn.marci.raft.rpc.netty.NettyRpcClient;
import cn.marci.raft.rpc.netty.NettyRpcServer;
import cn.marci.raft.serializer.SerializerSingleFactory;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.LockSupport;

@Slf4j
public class RpcTest {

    private static NettyRpcServer nettyRpcServer;

    public static class TestProcessor implements UserProcessor<Integer> {
        @Override
        public Object handleRequest(Integer cnt) {
            return "ooook!".repeat(cnt);
        }

        @Override
        public String interest() {
            return Integer.class.getName();
        }
    }

    public static void main(String[] args) {
        nettyRpcServer = new NettyRpcServer(8091, new SerializerSingleFactory());
        nettyRpcServer.registerUserProcessor(new TestProcessor());
        nettyRpcServer.start();
        LockSupport.park();
    }


    @Test
    public void startRpcClientAndTest() throws InterruptedException {
        ConnectionFactory connectionFactory = new ConnectionFactory(new SerializerSingleFactory());
        connectionFactory.start();
        NettyRpcClient nettyRpcClient = new NettyRpcClient(new ConnectionManager(connectionFactory));

        int cnt = 10;
        CountDownLatch countDownLatch = new CountDownLatch(cnt);
        for (int i = 0; i<cnt; i++) {
            nettyRpcClient.invokeAsync(new Endpoint("127.0.0.1", 8091), i)
                    .thenAcceptAsync(System.out::println)
                    .thenRun(countDownLatch::countDown);
        }
        System.out.println("send ok!");
        countDownLatch.await();
    }
}
