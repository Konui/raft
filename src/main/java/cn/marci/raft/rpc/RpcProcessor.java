package cn.marci.raft.rpc;

public interface RpcProcessor {

    String signature();

    Object process(Object... args);

}
