package cn.marci.raft.serializer;

import cn.marci.raft.common.SingleFactory;
import cn.marci.raft.serializer.hessian.HessianSerializer;

public class SerializerSingleFactory extends SingleFactory<Serializer> {

    @Override
    protected Serializer createInstance() {
        return new HessianSerializer();
    }

}
