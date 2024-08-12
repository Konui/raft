package cn.marci.raft.test.core;

import cn.marci.raft.utils.ThreadPoolUtils;
import com.lmax.disruptor.*;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import lombok.Data;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Stream;

public class DisruptorTest {

    @Data
    private static class TestData {
        private String name;
        private Integer score;
    }

    @Test
    public void test() throws InterruptedException {
        EventFactory<TestData> factory = new EventFactory<TestData>() {
            @Override
            public TestData newInstance() {
                return new TestData();
            }
        };

        EventHandler<TestData> handler = new EventHandler<TestData>() {

            @Override
            public void onEvent(TestData event, long sequence, boolean endOfBatch) throws Exception {
                long sleep = new Random().nextLong(0, Math.max(1, 5000 - event.getScore() * 100));
                TimeUnit.MILLISECONDS.sleep(sleep);
                System.out.printf("sequence=%d,endOfBatch=%b, %s%n", sequence, endOfBatch, event);
            }
        };

        Disruptor<TestData> disruptor = new Disruptor(factory,
                128,
                ThreadPoolUtils.getThreadFactory(false, "test"),
                ProducerType.MULTI,
                new BlockingWaitStrategy());

        disruptor.handleEventsWith(handler);
//        disruptor.setDefaultExceptionHandler();
        disruptor.start();

        RingBuffer<TestData> ringBuffer = disruptor.getRingBuffer();

        for (int i = 0; i < 100; i++) {
            int finalI = i;
            ringBuffer.publishEvent((event, sequence) -> {
                event.name = "a".repeat(finalI);
                event.score = finalI;
            });
        }
        LockSupport.park();
    }
}
