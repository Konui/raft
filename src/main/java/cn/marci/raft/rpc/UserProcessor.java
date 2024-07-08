package cn.marci.raft.rpc;

public interface UserProcessor<T> {

    Object handleRequest(T request);

    String interest();
}
