package cn.marci.raft.rpc;

import cn.marci.raft.common.Endpoint;
import cn.marci.raft.common.Lifecycle;

/**
 * 用于内部使用rpc调用的接口
 */
public interface RpcClient extends Lifecycle {

    /**
     * 同步调用
     */
    Object invokeSync(Endpoint endpoint, long timeout, String signature, Object... args);

    /**
     * 异步调用
     */
    Object invokeAsync(Endpoint endpoint, String signature, Object... args);

}
