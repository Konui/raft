package cn.marci.raft.core.rpc.dto;

import cn.marci.raft.common.Endpoint;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

@Data
@AllArgsConstructor
public class ClusterRequest implements Serializable {

    protected String group;

    protected Endpoint from;

    protected Endpoint to;

    public static class AddPeerRequest extends ClusterRequest {
        public AddPeerRequest(String group, Endpoint from, Endpoint to)  {
            super(group, from, to);
        }
    }

    public static class RemovePeerRequest extends ClusterRequest {
        public RemovePeerRequest(String group, Endpoint from, Endpoint to) {
            super(group, from, to);
        }
    }

}
