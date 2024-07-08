package cn.marci.raft.rpc;

import cn.marci.raft.common.Lifecycle;
import io.netty.util.internal.StringUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * 用于对外提供rpc调用的服务接口
 */
@Slf4j
public abstract class RpcServer implements Lifecycle {

    protected final int port;

    protected final AtomicBoolean running = new AtomicBoolean(false);

    protected final ConcurrentHashMap<String, UserProcessor> services = new ConcurrentHashMap<>();

    protected RpcServer(int port) {
        this.port = port;
    }

    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            Thread thread = new Thread(this::runServer);
            thread.setDaemon(true);
            thread.setName("RpcServer");
            thread.start();
            log.info("rpc server stated on port {}", port);
        }
    }

    @Override
    public void stop() {
        if (running.compareAndSet(true, false)) {
            close();
        }
    }

    protected abstract void runServer();

    protected abstract void close();

    public void registerUserProcessor(UserProcessor userProcessor) {
        if (StringUtil.isNullOrEmpty(userProcessor.interest())) {
            throw new IllegalArgumentException("userProcessor interest is null or empty, class: " + userProcessor.getClass().getName());
        }
        UserProcessor existProcess = services.putIfAbsent(userProcessor.interest(), userProcessor);
        if (existProcess != null) {
            throw new IllegalArgumentException("service already exist for " + userProcessor.interest());
        }
    }
}
