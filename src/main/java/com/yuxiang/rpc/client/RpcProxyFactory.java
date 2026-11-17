package com.yuxiang.rpc.client;

import com.yuxiang.rpc.model.RpcRequest;
import com.yuxiang.rpc.model.RpcResponse;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.Objects;
import java.util.UUID;

public class RpcProxyFactory {

    private final RpcClient rpcClient;

    public RpcProxyFactory(RpcClient rpcClient) {
        this.rpcClient = Objects.requireNonNull(
                rpcClient, "RPC 客户端不能为空"
        );
    }

    public <T> T createProxy(Class<T> serviceInterface) {
        Objects.requireNonNull(serviceInterface, "服务接口不能为空");

        if (!serviceInterface.isInterface()) {
            throw new IllegalArgumentException("服务类型必须是接口");
        }

        Object proxy = Proxy.newProxyInstance(
                serviceInterface.getClassLoader(),
                new Class<?>[]{serviceInterface},
                (proxyObject, method, args) -> {

                    // 基础对象方法在本地处理。
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" ->
                                    "RpcProxy[" + serviceInterface.getName() + "]";
                            case "hashCode" ->
                                    System.identityHashCode(proxyObject);
                            case "equals" ->
                                    proxyObject == args[0];
                            default ->
                                    throw new UnsupportedOperationException(
                                            "不支持的方法：" + method.getName()
                                    );
                        };
                    }

                    // 无参数方法的 args 可能为 null。
                    Object[] arguments =
                            args == null ? new Object[0] : args;

                    RpcRequest request = new RpcRequest(
                            UUID.randomUUID().toString(),
                            serviceInterface.getName(),
                            method.getName(),
                            method.getParameterTypes(),
                            arguments
                    );

                    RpcResponse response;

                    try {
                        response = rpcClient.send(request);
                    } catch (IOException | ClassNotFoundException e) {
                        throw new RpcException(
                                "RPC 通信失败，方法：" + method.getName()
                                        + "，requestId：" + request.requestId(),
                                e
                        );
                    }

                    if (!response.success()) {
                        throw new RpcException(
                                "远程调用失败，requestId：" + response.requestId()
                                        + "，原因：" + response.errorMessage()
                        );
                    }

                    return response.result();
                }
        );

        return serviceInterface.cast(proxy);
    }
}