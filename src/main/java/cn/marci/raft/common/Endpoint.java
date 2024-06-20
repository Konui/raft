package cn.marci.raft.common;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode
public class Endpoint {

    private final String ip;

    private final int port;

    public Endpoint(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }
}
