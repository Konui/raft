package cn.marci.raft.count;

import cn.marci.raft.common.Endpoint;
import cn.marci.raft.count.rpc.GetUserProcessor;
import cn.marci.raft.count.rpc.IncrementUserProcessor;
import cn.marci.raft.rpc.RpcClient;
import cn.marci.raft.rpc.RpcFactory;

public class CountClient {
    public static void main(String[] args) {
        RpcClient rpcClient = RpcFactory.getInstance().initClient();
        try {
            IncrementUserProcessor.IncrementUserRequest request = new IncrementUserProcessor.IncrementUserRequest(5);
//            GetUserProcessor.GetRequest request = new GetUserProcessor.GetRequest();
            Object o = rpcClient.invokeSync(new Endpoint("127.0.0.1", 8081), 10000, request);
            System.out.println(o);
        } finally {
            RpcFactory.getInstance().stop();
        }
    }
}
