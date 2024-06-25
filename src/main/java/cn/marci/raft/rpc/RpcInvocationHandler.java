package cn.marci.raft.rpc;

import cn.marci.raft.common.Endpoint;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Collectors;

public class RpcInvocationHandler implements InvocationHandler {

    private final long timeout;

    private final RpcClient rpcClient;

    public RpcInvocationHandler(long timeout, RpcClient rpcClient) {
        this.timeout = timeout;
        this.rpcClient = rpcClient;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (args == null || args.length == 0) {
            throw new RpcException("RpcInvocationHandler: args is null");
        }
        if (!(args[0] instanceof Endpoint)) {
            throw new RpcException("RpcInvocationHandler: args[0] is not Endpoint");
        }
        return rpcClient.invokeSync((Endpoint) args[0], timeout, RpcServer.generateSignature(method), args);
    }



}
