package cn.marci.raft.core.conf;

import cn.marci.raft.conf.PropertiesManager;
import lombok.Getter;

import static cn.marci.raft.core.conf.ConfConstants.*;

@Getter
public class RaftConf {

    private int rpcServerPort = PropertiesManager.getInt(RAFT_RPC_SERVER_PORT, 8081);

    private String nodes = PropertiesManager.getConf(RAFT_NODES);

}
