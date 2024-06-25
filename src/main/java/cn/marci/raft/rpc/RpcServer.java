package cn.marci.raft.rpc;

import cn.marci.raft.common.Lifecycle;
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

    protected final ConcurrentHashMap<String, MethodMetadata> services = new ConcurrentHashMap<>();

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

    public <T> void registerService(Class<T> intf, T service) {
        if (service == null) {
            throw new RpcException("service is null");
        }
        Method[] declaredMethods = service.getClass().getDeclaredMethods();

        for (Method method : declaredMethods) {
            String signature = generateSignature(intf, method);
            if (services.containsKey(signature)) {
                throw new RpcException(String.format("service %s already registered", signature));
            }
            services.put(signature, new MethodMetadata(service, method));
        }
    }

    public static String generateSignature(Method method) {
        return generateSignature(method.getDeclaringClass(), method);
    }

    public static String generateSignature(Class<?> clz, Method method) {
        String paramsStr = Arrays.stream(method.getParameterTypes())
                .map(Class::getName)
                .collect(Collectors.joining(","));
        return String.format("%s_%s_%s", clz.getName(), method.getName(), paramsStr);
    }

    @Data
    @AllArgsConstructor
    public static class MethodMetadata {
        private final Object target;

        private final Method method;

        public Object invoke(Object[] args) {
            try {
                return method.invoke(target, args);
            } catch (Exception e) {
                throw new RpcException(e);
            }
        }
    }
}
