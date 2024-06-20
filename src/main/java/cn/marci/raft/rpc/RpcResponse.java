package cn.marci.raft.rpc;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

import java.io.Serializable;

@Data
@AllArgsConstructor
@ToString
public class RpcResponse implements Serializable {
    private Long id;

    private boolean success;

    private Object data;

    private String msg;

    private Throwable throwable;

    public RpcResponse(Long id, Object data) {
        this.id = id;
        this.data = data;
        this.success = true;
    }

}
