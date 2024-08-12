package cn.marci.raft.core.rpc.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

@Data
@AllArgsConstructor
public class ClusterResponse implements Serializable {

    private boolean success;

}
