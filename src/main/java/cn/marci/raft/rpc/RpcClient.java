package cn.marci.raft.rpc;

import cn.marci.raft.common.Endpoint;
import cn.marci.raft.common.Lifecycle;

import java.util.concurrent.CompletableFuture;

/**
 * 用于内部使用rpc调用的接口
 * interest参数为UserProcessor接口中的interest返回值，默认为UserProcessor接口中RPC request的class name
 */
public interface RpcClient extends Lifecycle {

    /**
     * 同步调用
     */
    Object invokeSync(Endpoint endpoint, long timeout, Object arg, String interest);

    Object invokeSync(Endpoint endpoint, long timeout, Object arg);
    /**
     * 异步调用
     */
    CompletableFuture invokeAsync(Endpoint endpoint, Object arg, String interest);

    CompletableFuture invokeAsync(Endpoint endpoint, Object arg);

}
