package cn.marci.raft.serializer;

import cn.marci.raft.common.Factory;
import cn.marci.raft.serializer.hessian.HessianSerializer;

public class SerializerFactory extends Factory<Serializer> {

    @Override
    protected Serializer createInstance() {
        return new HessianSerializer();
    }

}
