package com.yuxiang.rpc.model;

import java.io.Serial;
import java.io.Serializable;

public record RpcResponse(
        String requestId,
        boolean success,
        Object result,
        String errorMessage
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static RpcResponse success(String requestId, Object result) {
        return new RpcResponse(requestId, true, result, null);
    }

    public static RpcResponse failure(
            String requestId,
            String errorMessage
    ) {
        return new RpcResponse(requestId, false, null, errorMessage);
    }
}