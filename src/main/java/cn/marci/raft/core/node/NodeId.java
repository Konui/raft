package cn.marci.raft.core.node;

import cn.marci.raft.common.Endpoint;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

@Data
@ToString
@AllArgsConstructor
public class NodeId {

    private String group;

    private Endpoint endpoint;

}
