package cn.marci.raft.common;

import cn.marci.raft.utils.NetUtils;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.io.Serializable;
import java.util.Objects;

@Data
public class Endpoint implements Serializable {

    private final String ip;

    private final int port;

    private String str;

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

        if ("127.0.0.1".equals(split[0]) || "localhost".equalsIgnoreCase(split[0])) {
            ip = NetUtils.getLocalIp();
        } else {
            ip = split[0];
        }
        this.port = Integer.parseInt(split[1]);
    }

    @Override
    public String toString() {
        if (str == null) {
            str = "(" + ip + ":"+ port + ")";
        }
        return str;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof Endpoint endpoint)) return false;
        return port == endpoint.port && Objects.equals(ip, endpoint.ip);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ip, port);
    }
}
