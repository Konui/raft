package cn.marci.raft.core.conf;

public interface ConfConstants {

    String RAFT_RPC_SERVER_PORT = "raft.rpc.server.port";

    String RAFT_NODES = "raft.nodes";

    String RAFT_ELECTION_MIN_TIMEOUT = "raft.election.min.timeout";

    String RAFT_ELECTION_MAX_TIMEOUT = "raft.election.max.timeout";

    String RAFT_HEARTBEAT_INTERVAL = "raft.heartbeat.interval";
}
