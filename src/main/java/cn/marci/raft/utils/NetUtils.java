package cn.marci.raft.utils;

import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;

@Slf4j
public class NetUtils {

    public static String getLocalIp() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            log.error("get local ip error", e);
            throw new RuntimeException("get local ip error");
        }
    }
}
