package cn.marci.raft.core.node;

public interface StateMachine {

    void onApply(OperationMeta meta);

    void onLeaderStart(long term);

    void onLeaderStop();
}
