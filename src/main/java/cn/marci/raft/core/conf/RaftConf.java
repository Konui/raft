package cn.marci.raft.core.conf;

import cn.marci.raft.common.Endpoint;
import cn.marci.raft.conf.PropertiesManager;
import cn.marci.raft.utils.NetUtils;
import lombok.Getter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static cn.marci.raft.core.conf.ConfConstants.*;

@Getter
public class RaftConf {

    public static final RaftConf INSTANCE = new RaftConf();

    private int rpcServerPort = PropertiesManager.getInt(RAFT_RPC_SERVER_PORT, 8081);

    private String nodes = PropertiesManager.getConf(RAFT_NODES);

    private long electionMinTimeout = PropertiesManager.getLong(RAFT_ELECTION_MIN_TIMEOUT, 150);

    private long electionMaxTimeout = PropertiesManager.getLong(RAFT_ELECTION_MAX_TIMEOUT, 350);

    private long heartbeatInterval = PropertiesManager.getLong(RAFT_HEARTBEAT_INTERVAL, 100);

    public List<Endpoint> getClusterNodes() {
        if (nodes == null || nodes.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(nodes.split(",|;"))
                .map(Endpoint::new)
                .toList();
    }

    public static RaftConf getInstance() {
        return INSTANCE;
    }
}
