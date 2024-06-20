package cn.marci.raft.rpc.netty;

import cn.marci.raft.serializer.Serializer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class NettyRpcEncoder extends MessageToByteEncoder {

    private final Serializer serializer;

    public NettyRpcEncoder(Serializer serializer) {
        this.serializer = serializer;
    }

    @Override
    protected void encode(ChannelHandlerContext channelHandlerContext, Object o, ByteBuf byteBuf) throws Exception {
        byte[] data = serializer.serialize(o);
        byteBuf.writeInt(data.length);
        byteBuf.writeBytes(data);
    }

}
