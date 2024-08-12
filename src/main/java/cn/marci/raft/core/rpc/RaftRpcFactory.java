package cn.marci.raft.core.rpc;

import cn.marci.raft.core.conf.RaftConf;
import cn.marci.raft.core.rpc.impl.RpcServiceImpl;
import cn.marci.raft.core.rpc.processor.AppendEntriesProcessor;
import cn.marci.raft.core.rpc.processor.ClusterProcessor;
import cn.marci.raft.core.rpc.processor.RequestVoteProcessor;
import cn.marci.raft.rpc.RpcClient;
import cn.marci.raft.rpc.RpcFactory;

public class RaftRpcFactory {

    public static final RaftRpcFactory INSTANCE = new RaftRpcFactory();

    private RpcService rpcService;

    public static RaftRpcFactory getInstance() {
        return INSTANCE;
    }

    private void init() {
        RpcFactory rpcFactory = RpcFactory.getInstance();
        //client
        RpcClient rpcClient = rpcFactory.initClient();
        rpcService = new RpcServiceImpl(rpcClient);

        //server
        rpcFactory.initServer(RaftConf.getInstance().getRpcServerPort());
        rpcFactory.registerUserProcessor(new RequestVoteProcessor());
        rpcFactory.registerUserProcessor(new AppendEntriesProcessor());

        rpcFactory.registerUserProcessor(new ClusterProcessor.AddPeerProcessor());
        rpcFactory.registerUserProcessor(new ClusterProcessor.RemovePeerProcessor());
    }

    public synchronized RpcService getRpcService() {
        if (rpcService == null) {
            init();
        }
        return rpcService;
    }

}
