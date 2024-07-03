package cn.marci.raft.common;

import cn.marci.raft.utils.NetUtils;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.io.Serializable;

@Data
@ToString
@EqualsAndHashCode
public class Endpoint implements Serializable {

    private final String ip;

    private final int port;

    public Endpoint(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    public Endpoint(String host) {
        if (host == null) {
            throw new IllegalArgumentException("host should not be null");
        }
        String[] split = host.split(":");
        if (split.length != 2) {
            throw new IllegalArgumentException("invalid host format");
        }

        ;
        if ("127.0.0.1".equals(split[0]) || "localhost".equalsIgnoreCase(split[0])) {
            ip = NetUtils.getLocalIp();
        } else {
            ip = split[0];
        }
        this.port = Integer.parseInt(split[1]);
    }
}
