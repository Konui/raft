package cn.marci.raft.core.rpc.processor;

import cn.marci.raft.core.node.Node;
import cn.marci.raft.core.node.NodeManager;
import cn.marci.raft.core.rpc.dto.AppendEntriesRequest;
import cn.marci.raft.rpc.UserProcessor;

public class AppendEntriesProcessor implements UserProcessor<AppendEntriesRequest> {
    @Override
    public Object handleRequest(AppendEntriesRequest request) {
        Node node = NodeManager.getInstance().get(request.getGroup(), request.getToEndpoint());
        return node.handleAppendEntries(request);
    }

    @Override
    public String interest() {
        return AppendEntriesRequest.class.getName();
    }
}
