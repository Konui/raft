package cn.marci.raft.count.rpc;

import cn.marci.raft.count.CountCompletableFuture;
import cn.marci.raft.count.CountService;
import cn.marci.raft.rpc.UserProcessor;

import java.io.Serializable;
import java.util.function.Consumer;

import static cn.marci.raft.rpc.UserProcessor.HandlerType.ASYNC_SEND_RESP_BY_USER;

public class GetUserProcessor implements UserProcessor<GetUserProcessor.GetRequest> {

    private final CountService countService;

    public GetUserProcessor(CountService countService) {
        this.countService = countService;
    }

    @Override
    public Object handleRequest(GetRequest request) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String interest() {
        return GetRequest.class.getName();
    }

    @Override
    public void handleRequestAsync(GetUserProcessor.GetRequest request, Consumer<Object> sendCallback) {
        CountCompletableFuture future = new CountCompletableFuture();
        future.thenAccept(sendCallback);
        countService.get(future);
    }

    @Override
    public HandlerType handlerType() {
        return ASYNC_SEND_RESP_BY_USER;
    }

    public static class GetRequest implements Serializable {

    }
}
