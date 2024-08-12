package cn.marci.raft.count;

import cn.marci.raft.core.node.Node;
import cn.marci.raft.core.node.NodeManager;
import cn.marci.raft.count.rpc.GetUserProcessor;
import cn.marci.raft.count.rpc.IncrementUserProcessor;
import cn.marci.raft.rpc.RpcFactory;
import lombok.Getter;

import java.util.concurrent.locks.LockSupport;

@Getter
public class CountServer {

    private Node node;

    private CountStateMachine csm;

    private void init() {
        this.csm = new CountStateMachine();
        this.node = NodeManager.getInstance().create("count_test", csm);
        this.node.start();
        CountService countService = new CountService(this);
        RpcFactory.getInstance().registerUserProcessor(new IncrementUserProcessor(countService));
        RpcFactory.getInstance().registerUserProcessor(new GetUserProcessor(countService));
    }

    public static void main(String[] args) {
        CountServer countServer = new CountServer();
        countServer.init();
        LockSupport.park();
    }

}
