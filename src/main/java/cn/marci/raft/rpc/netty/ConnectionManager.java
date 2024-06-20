package cn.marci.raft.rpc.netty;

import cn.marci.raft.common.Endpoint;

import java.security.interfaces.EdECKey;
import java.util.concurrent.ConcurrentHashMap;

public class ConnectionManager {

    ConcurrentHashMap<Endpoint, Connection> connections = new ConcurrentHashMap<>();

    private final ConnectionFactory connectionFactory;

    public ConnectionManager(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    public Connection getOrCreate(Endpoint endpoint) {
        Connection connection = connections.computeIfAbsent(endpoint, connectionFactory::createConnection);
        if (!connection.getChannel().isActive()) {
            connection.stop();
            connection = connectionFactory.createConnection(endpoint);
            connections.put(endpoint, connection);
        }
        return connection;
    }

}
