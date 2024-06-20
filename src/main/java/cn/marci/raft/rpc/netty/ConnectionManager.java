package cn.marci.raft.rpc.netty;

import cn.marci.raft.common.Endpoint;

import java.util.concurrent.ConcurrentHashMap;

public class ConnectionManager {
    //TODO 获取连接前校验，连接失效后移除

    ConcurrentHashMap<Endpoint, Connection> connections = new ConcurrentHashMap<>();

    private final ConnectionFactory connectionFactory;

    public ConnectionManager(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    public Connection getOrCreate(Endpoint endpoint) {
        return connections.computeIfAbsent(endpoint, connectionFactory::createConnection);
    }

}
