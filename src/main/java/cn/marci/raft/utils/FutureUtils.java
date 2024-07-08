package cn.marci.raft.utils;

import org.slf4j.Logger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

public class FutureUtils {

    public static <T> CompletableFuture<T> addHandleExceptionStage(CompletableFuture<T> cf, Logger log) {
        return addHandleExceptionStage(cf, log, "CompletableFuture run error", null);
    }

    public static <T> CompletableFuture<T> addHandleExceptionStage(CompletableFuture<T> cf, Logger log, String msg) {
        return addHandleExceptionStage(cf, log, msg, null);
    }

    public static <T> CompletableFuture<T> addHandleExceptionStage(CompletableFuture<T> cf, Logger log, String msg, T defaultValue) {
        if (cf == null) {
            throw new IllegalArgumentException("CompletableFuture is null");
        }
        return cf.exceptionally(err -> {
            Throwable throwable = extractRealException(err);
            log.error(msg, throwable);
            return defaultValue;
        });
    }

    public static Throwable extractRealException(Throwable throwable) {
        //这里判断异常类型是否为CompletionException、ExecutionException，如果是则进行提取，否则直接返回。
        if (throwable instanceof CompletionException || throwable instanceof ExecutionException) {
            if (throwable.getCause() != null) {
                return throwable.getCause();
            }
        }
        return throwable;
    }
}
