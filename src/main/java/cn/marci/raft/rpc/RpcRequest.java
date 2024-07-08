package cn.marci.raft.rpc;

import cn.marci.raft.rpc.netty.IDGenerator;
import lombok.Data;
import lombok.ToString;

import java.io.Serializable;

@Data
@ToString
public class RpcRequest implements Serializable {

    private Long id = IDGenerator.nextId();

    private Object arg;

    private String interest;

    public RpcRequest(Object arg) {
        this.arg = arg;
        if (arg == null) {
            throw new IllegalArgumentException("Rpc request arg can't null");
        }
        this.interest = arg.getClass().getName();
    }

    public RpcRequest(Object arg, String interest) {
        this.arg = arg;
        this.interest = interest;
    }
}
