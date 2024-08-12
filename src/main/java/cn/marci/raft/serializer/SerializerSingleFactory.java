package cn.marci.raft.serializer;

import cn.marci.raft.serializer.hessian.HessianSerializer;

public class SerializerSingleFactory {

    public static Serializer getInstance() {
        return InstanceHolder.INSTANCE;
    }

    private static class InstanceHolder {
        private static final Serializer INSTANCE = new HessianSerializer();
    }

}
