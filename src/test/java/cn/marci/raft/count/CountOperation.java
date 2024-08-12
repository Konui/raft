package cn.marci.raft.count;

import lombok.Data;

import java.io.Serializable;

@Data
public class CountOperation implements Serializable {

    public enum Operation {
        GET,
        INCREMENT;
    }

    private int op;

    private long data;

    public Operation getOpEnum() {
        return Operation.values()[op];
    }

    public CountOperation(int op) {
        this.op = op;
    }

    public CountOperation(int op, long data) {
        this.op = op;
        this.data = data;
    }
}
