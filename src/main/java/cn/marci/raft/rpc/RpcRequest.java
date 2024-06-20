package cn.marci.raft.rpc;

import cn.marci.raft.rpc.netty.IDGenerator;
import lombok.Data;
import lombok.ToString;

import java.io.Serializable;

@Data
@ToString
public class RpcRequest implements Serializable {

    private Long id = IDGenerator.nextId();

    private String signature;

    private Object[] args;

    public RpcRequest(String signature, Object[] args) {
        this.signature = signature;
        this.args = args;
    }
}
