package cn.marci.raft.utils;

import cn.marci.raft.common.Endpoint;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;

@Slf4j
public class NetUtils {

    public static boolean isLocalhost(String ip) {
        return "localhost".equals(ip) || "127.0.0.1".equals(ip);
    }

    public static int getCpuCount() {
        return Runtime.getRuntime().availableProcessors();
    }

    public static String getLocalIp() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            log.error("get local ip error", e);
            throw new RuntimeException("get local ip error");
        }
    }
}
