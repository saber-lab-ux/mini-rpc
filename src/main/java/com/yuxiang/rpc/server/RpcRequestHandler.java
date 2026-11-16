package com.yuxiang.rpc.server;

import com.yuxiang.rpc.model.RpcRequest;
import com.yuxiang.rpc.model.RpcResponse;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Objects;

public class RpcRequestHandler {

    private final ServiceRegistry serviceRegistry;

    public RpcRequestHandler(ServiceRegistry serviceRegistry) {
        this.serviceRegistry = Objects.requireNonNull(
                serviceRegistry,
                "服务注册表不能为空"
        );
    }

    public RpcResponse handle(RpcRequest request) {
        if (request == null) {
            return RpcResponse.failure(null, "请求不能为空");
        }

        String requestId = request.requestId();

        try {
            validateRequest(request);

            ServiceRegistry.RegisteredService service =
                    serviceRegistry.getRegistration(
                            request.serviceName()
                    );

            Method method = service.serviceInterface().getMethod(
                    request.methodName(),
                    request.parameterTypes()
            );

            if (Modifier.isStatic(method.getModifiers())) {
                return RpcResponse.failure(
                        requestId,
                        "不支持调用静态方法"
                );
            }

            Object result = method.invoke(
                    service.implementation(),
                    request.arguments()
            );

            return RpcResponse.success(requestId, result);

        } catch (NoSuchMethodException e) {
            return RpcResponse.failure(
                    requestId,
                    "接口中不存在指定方法或参数类型不匹配"
            );

        } catch (InvocationTargetException e) {
            // 业务方法抛出的异常会被反射包装，记录真正的原因。
            Throwable cause = e.getCause();

            System.err.println(
                    "业务执行失败，requestId=" + requestId
            );

            if (cause != null) {
                cause.printStackTrace();
            }

            return RpcResponse.failure(
                    requestId,
                    "服务方法执行失败，请查看服务端日志"
            );

        } catch (IllegalArgumentException e) {
            return RpcResponse.failure(
                    requestId,
                    e.getMessage()
            );

        } catch (IllegalAccessException e) {
            return RpcResponse.failure(
                    requestId,
                    "服务方法不可访问"
            );

        } catch (Exception e) {
            System.err.println(
                    "请求处理异常，requestId=" + requestId
            );
            e.printStackTrace();

            return RpcResponse.failure(
                    requestId,
                    "服务端内部错误"
            );
        }
    }

    private void validateRequest(RpcRequest request) {
        if (request.requestId() == null
                || request.requestId().isBlank()) {
            throw new IllegalArgumentException("请求 ID 不能为空");
        }

        if (request.serviceName() == null
                || request.serviceName().isBlank()) {
            throw new IllegalArgumentException("服务名称不能为空");
        }

        if (request.methodName() == null
                || request.methodName().isBlank()) {
            throw new IllegalArgumentException("方法名称不能为空");
        }

        if (request.parameterTypes() == null
                || request.arguments() == null) {
            throw new IllegalArgumentException(
                    "参数类型数组和参数数组不能为空"
            );
        }

        if (request.parameterTypes().length
                != request.arguments().length) {
            throw new IllegalArgumentException(
                    "参数类型数量与参数数量不一致"
            );
        }

        for (Class<?> parameterType : request.parameterTypes()) {
            if (parameterType == null) {
                throw new IllegalArgumentException(
                        "参数类型不能为 null"
                );
            }
        }
    }
}