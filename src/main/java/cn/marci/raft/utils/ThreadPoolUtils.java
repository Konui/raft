package cn.marci.raft.utils;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class ThreadPoolUtils {

    private static final Thread.UncaughtExceptionHandler LOG_UNCAUGHT_EXCEPTION_HANDLER = (t, e) -> {
        log.error("Uncaught exception in thread {}", t.getName(), e);
    };

    public static ThreadFactory getThreadFactory(boolean daemon, String prefix) {
        return new ThreadFactory() {

            private AtomicLong count = new AtomicLong(0);

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r);
                thread.setDaemon(daemon);
                thread.setUncaughtExceptionHandler(LOG_UNCAUGHT_EXCEPTION_HANDLER);
                thread.setName(String.format("%s-%d", prefix, count.getAndIncrement()));
                return thread;
            }
        };
    }
}
