package cn.marci.raft.core.schedule;

import cn.marci.raft.common.Lifecycle;
import cn.marci.raft.utils.ThreadPoolUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
public abstract class Timer implements Lifecycle {

    protected final ScheduledThreadPoolExecutor scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(1, ThreadPoolUtils.getThreadFactory(true, timerName()));

    protected volatile ScheduledFuture lastFuture;

    @Override
    public void start() {
        schedule();
    }

    public void stop() {
        if (lastFuture != null) {
            lastFuture.cancel(false);
        }
        log.debug("{} timer cancel", timerName());
    }

    public void reset() {
        if (lastFuture != null) {
            lastFuture.cancel(false);
        }
        log.debug("{} timer reset", timerName());
        schedule();
    }

    private void schedule() {
        long delay = nextDelay();
        log.debug("{} timer will run after {} ms", timerName(), delay);
        lastFuture = scheduledThreadPoolExecutor.schedule(() -> {
            try {
                run();
            } catch (Exception e) {
                log.error( "{} timer error", timerName(), e);
            }
            if (isRepeatTask()) {
                schedule();
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    protected String timerName() {
        return this.getClass().getSimpleName();
    }

    protected abstract void run();

    protected abstract long nextDelay();

    protected boolean isRepeatTask() {
        return true;
    }
}
