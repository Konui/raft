package cn.marci.raft.core.node;

import cn.marci.raft.core.log.LogManager;

public interface FSMCaller {

    void init(CallbackQueue callbackQueue, StateMachine stateMachine, LogManager logManager);

    boolean onCommitted(long committedIndex);

    void onLeaderStart(long term);

    void onLeaderStop();
}
