package cn.marci.raft.core.node;

import cn.marci.raft.common.Endpoint;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.io.Serializable;

@Data
@ToString
@AllArgsConstructor
@EqualsAndHashCode
public class NodeId implements Serializable {

    private String group;

    private Endpoint endpoint;

}
