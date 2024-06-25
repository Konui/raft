package cn.marci.raft.rpc;

import java.lang.reflect.Proxy;
import java.util.Arrays;

public class RpcConsumerFactory {

    private final long timeout = 100;

    private final RpcClient rpcClient;

    private final RpcInvocationHandler rpcInvocationHandler;

    public RpcConsumerFactory(RpcClient rpcClient) {
        this.rpcClient = rpcClient;
        this.rpcInvocationHandler = new RpcInvocationHandler(100, rpcClient);
    }

    public <T> T getClient(Class<T> clz) {
        return (T) Proxy.newProxyInstance(clz.getClassLoader(), new Class[]{clz}, rpcInvocationHandler);
    }
}
