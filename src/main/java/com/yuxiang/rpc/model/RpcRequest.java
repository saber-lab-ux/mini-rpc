package com.yuxiang.rpc.model;

import java.io.Serial;
import java.io.Serializable;

public record RpcRequest(
        String requestId,
        String serviceName,
        String methodName,
        Class<?>[] parameterTypes,
        Object[] arguments
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}