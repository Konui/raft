package cn.marci.raft.core.node;

import cn.marci.raft.common.Endpoint;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.io.Serializable;
import java.util.Objects;

@Data
public class NodeId implements Serializable {

    private String group;

    private Endpoint endpoint;

    private String str;

    public NodeId(String group, Endpoint endpoint) {
        this.group = group;
        this.endpoint = endpoint;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof NodeId nodeId)) return false;
        return Objects.equals(group, nodeId.group) && Objects.equals(endpoint, nodeId.endpoint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(group, endpoint);
    }

    @Override
    public String toString() {
        if (str == null) {
           str = "[" + group + "]" + endpoint;
        }
        return str;
    }
}
