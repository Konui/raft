package cn.marci.raft.count.rpc;

import cn.marci.raft.count.CountCompletableFuture;
import cn.marci.raft.count.CountService;
import cn.marci.raft.rpc.UserProcessor;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;
import java.util.function.Consumer;

import static cn.marci.raft.rpc.UserProcessor.HandlerType.ASYNC_SEND_RESP_BY_USER;

public class IncrementUserProcessor implements UserProcessor<IncrementUserProcessor.IncrementUserRequest> {

    private final CountService countService;

    public IncrementUserProcessor(CountService countService) {
        this.countService = countService;
    }

    @Override
    public Object handleRequest(IncrementUserRequest request) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String interest() {
        return IncrementUserRequest.class.getName();
    }

    @Override
    public void handleRequestAsync(IncrementUserRequest request, Consumer<Object> sendCallback) {
        CountCompletableFuture future = new CountCompletableFuture();
        future.thenAccept(sendCallback);
        countService.increment(request.getData(), future);
    }

    @Override
    public HandlerType handlerType() {
        return ASYNC_SEND_RESP_BY_USER;
    }

    @Data
    @AllArgsConstructor
    public static class IncrementUserRequest implements Serializable {
        private long data;
    }
}
