package com.yuxiang.rpc.client;

import com.yuxiang.rpc.model.RpcRequest;
import com.yuxiang.rpc.model.RpcResponse;

import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Objects;

public class RpcClient {

    private final String host;
    private final int port;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;

    public RpcClient(String host, int port) {
        this(host, port, 3000, 5000);
    }

    public RpcClient(
            String host,
            int port,
            int connectTimeoutMillis,
            int readTimeoutMillis
    ) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("服务端地址不能为空");
        }

        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("端口必须在 1～65535 之间");
        }

        if (connectTimeoutMillis <= 0 || readTimeoutMillis <= 0) {
            throw new IllegalArgumentException("超时时间必须大于 0 毫秒");
        }

        this.host = host;
        this.port = port;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
    }

    public RpcResponse send(RpcRequest request)
            throws IOException, ClassNotFoundException {

        Objects.requireNonNull(request, "请求不能为空");

        if (request.requestId() == null
                || request.requestId().isBlank()) {
            throw new IllegalArgumentException("请求 ID 不能为空");
        }

        try (Socket socket = new Socket()) {
            // 建立连接最多等待 3 秒。
            socket.connect(
                    new InetSocketAddress(host, port),
                    connectTimeoutMillis
            );
            // 等待网络数据最多阻塞 5 秒。
            socket.setSoTimeout(readTimeoutMillis);

            try (ObjectOutputStream output =
                         new ObjectOutputStream(socket.getOutputStream())) {

                // 先发送对象流头部，避免两端相互等待。
                output.flush();

                try (ObjectInputStream input =
                             new ObjectInputStream(socket.getInputStream())) {

                    input.setObjectInputFilter(RpcClient::filterResponse);

                    output.writeObject(request);
                    output.flush();

                    Object received = input.readObject();

                    if (!(received instanceof RpcResponse response)) {
                        throw new IOException(
                                "服务端返回的对象不是 RpcResponse"
                        );
                    }

                    if (!request.requestId().equals(response.requestId())) {
                        throw new IOException(
                                "响应 ID 与请求 ID 不一致"
                        );
                    }

                    return response;
                }
            }
        }
    }

    private static ObjectInputFilter.Status filterResponse(
            ObjectInputFilter.FilterInfo info
    ) {
        if (info.depth() > 10
                || info.references() > 1000
                || info.streamBytes() > 1024 * 1024
                || info.arrayLength() > 1000) {

            return ObjectInputFilter.Status.REJECTED;
        }

        Class<?> type = info.serialClass();

        if (type == null) {
            return ObjectInputFilter.Status.UNDECIDED;
        }

        while (type.isArray()) {
            type = type.getComponentType();
        }

        // 当前示例的返回值是 String。
        if (type.isPrimitive()
                || type == RpcResponse.class
                || type == String.class
                || type == Object.class) {

            return ObjectInputFilter.Status.ALLOWED;
        }

        return ObjectInputFilter.Status.REJECTED;
    }
}