package cn.marci.raft.rpc;

import java.util.function.Consumer;

public interface UserProcessor<T> {

    enum HandlerType {
        SYNC,
        ASYNC,
        ASYNC_SEND_RESP_BY_USER,
    }

    Object handleRequest(T request);

    default void handleRequestAsync(T request, Consumer<Object> sendCallback) {
        throw new UnsupportedOperationException();
    }

    String interest();

    default HandlerType handlerType() {
        return HandlerType.ASYNC;
    }

}
