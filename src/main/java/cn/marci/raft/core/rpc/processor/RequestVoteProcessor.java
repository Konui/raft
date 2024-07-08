package cn.marci.raft.core.rpc.processor;

import cn.marci.raft.core.node.Node;
import cn.marci.raft.core.node.NodeManager;
import cn.marci.raft.core.rpc.dto.RequestVoteRequest;
import cn.marci.raft.rpc.UserProcessor;

public class RequestVoteProcessor implements UserProcessor<RequestVoteRequest> {
    @Override
    public Object handleRequest(RequestVoteRequest request) {
        Node node = NodeManager.getInstance().get(request.getGroup(), request.getToEndpoint());
        if (request.isPreVote()) {
            return node.handlePreVote(request);
        } else {
            return node.handleVote(request);
        }
    }

    @Override
    public String interest() {
        return RequestVoteRequest.class.getName();
    }
}
