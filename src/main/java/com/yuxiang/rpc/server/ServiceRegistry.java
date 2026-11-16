package com.yuxiang.rpc.server;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class ServiceRegistry {

    private final Map<String, RegisteredService> services =
            new ConcurrentHashMap<>();

    public <T> void register(Class<T> serviceInterface, T implementation) {
        Objects.requireNonNull(serviceInterface, "服务接口不能为空");
        Objects.requireNonNull(implementation, "服务实现不能为空");

        if (!serviceInterface.isInterface()) {
            throw new IllegalArgumentException("服务类型必须是接口");
        }

        if (!serviceInterface.isInstance(implementation)) {
            throw new IllegalArgumentException("服务实现必须实现指定接口");
        }

        String serviceName = serviceInterface.getName();

        RegisteredService registeredService =
                new RegisteredService(serviceInterface, implementation);

        RegisteredService existing =
                services.putIfAbsent(serviceName, registeredService);

        if (existing != null) {
            throw new IllegalStateException(
                    "服务已经注册：" + serviceName
            );
        }
    }

    public RegisteredService getRegistration(String serviceName) {
        if (serviceName == null || serviceName.isBlank()) {
            throw new IllegalArgumentException("服务名称不能为空");
        }

        RegisteredService service = services.get(serviceName);

        if (service == null) {
            throw new IllegalArgumentException(
                    "未找到服务：" + serviceName
            );
        }

        return service;
    }

    public Object getService(String serviceName) {
        return getRegistration(serviceName).implementation();
    }

    public record RegisteredService(
            Class<?> serviceInterface,
            Object implementation
    ) {
    }
}