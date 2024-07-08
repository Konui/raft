package cn.marci.raft.test.utils;

import cn.marci.raft.utils.FutureUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
public class FutureUtilsTest {

    @Test
    public void testFutureException() {
        CompletableFuture<Void> cf = CompletableFuture.supplyAsync(() -> {
            sleep(1);
            return 1;
        }).thenAcceptAsync(r -> {
            throw new RuntimeException("test");
        });

        FutureUtils.addHandleExceptionStage(cf, log);
        sleep(2);
    }

    private void sleep(long seconds) {
        try {
            TimeUnit.SECONDS.sleep(1);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}
