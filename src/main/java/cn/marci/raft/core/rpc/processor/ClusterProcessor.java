package cn.marci.raft.core.rpc.processor;

import cn.marci.raft.core.node.Node;
import cn.marci.raft.core.node.NodeManager;
import cn.marci.raft.core.rpc.dto.ClusterRequest;
import cn.marci.raft.core.rpc.dto.ClusterResponse;
import cn.marci.raft.rpc.UserProcessor;

public class ClusterProcessor {

    public static class AddPeerProcessor implements UserProcessor<ClusterRequest.AddPeerRequest> {

        @Override
        public Object handleRequest(ClusterRequest.AddPeerRequest request) {
            Node node = NodeManager.getInstance().get(request.getGroup(), request.getTo());
            node.addPeer(request.getFrom());
            return new ClusterResponse(true);
        }

        @Override
        public String interest() {
            return ClusterRequest.AddPeerRequest.class.getName();
        }
    }

    public static class RemovePeerProcessor implements UserProcessor<ClusterRequest.RemovePeerRequest> {

        @Override
        public Object handleRequest(ClusterRequest.RemovePeerRequest request) {
            Node node = NodeManager.getInstance().get(request.getGroup(), request.getTo());
            node.removePeer(request.getFrom());
            return new ClusterResponse(true);
        }

        @Override
        public String interest() {
            return ClusterRequest.RemovePeerRequest.class.getName();
        }
    }
}
