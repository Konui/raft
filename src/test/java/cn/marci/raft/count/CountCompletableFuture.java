package cn.marci.raft.count;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;
import java.util.concurrent.CompletableFuture;

@Data
public class CountCompletableFuture extends CompletableFuture<CountCompletableFuture.ValueResponse> {

    private CountOperation countOperation;


    @Data
    @AllArgsConstructor
    public static class ValueResponse implements Serializable {
        private boolean success;
        private long value;
        private String errMsg;

        private String redirect;
    }

    public static ValueResponse success(long value) {
        return new ValueResponse(true, value, null, null);
    }

    public static ValueResponse failure(String errMsg, String redirect) {
        return new ValueResponse(false, 0, errMsg, redirect);
    }

}
