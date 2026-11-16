package com.yuxiang.rpc.server;

import com.yuxiang.rpc.model.RpcRequest;
import com.yuxiang.rpc.model.RpcResponse;

import java.io.IOException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class RpcServer {

    private final int port;
    private final RpcRequestHandler requestHandler;

    public RpcServer(int port, ServiceRegistry serviceRegistry) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("端口必须在 1～65535 之间");
        }

        this.port = port;
        this.requestHandler = new RpcRequestHandler(serviceRegistry);
    }

    public void start() throws IOException {
        // 第一版只监听本机，客户端使用 127.0.0.1 连接。
        InetAddress address = InetAddress.getByName("127.0.0.1");

        try (ServerSocket serverSocket =
                     new ServerSocket(port, 50, address)) {

            System.out.println(
                    "RPC 服务端已启动，监听 127.0.0.1:" + port
            );

            while (!Thread.currentThread().isInterrupted()) {
                Socket socket = serverSocket.accept();

                // 先采用逐个处理连接的方式，后续再加入并发处理。
                handleConnection(socket);
            }
        }
    }

    private void handleConnection(Socket socket) {
        try (socket) {
            // 读取客户端数据时，最多等待 5 秒。
            socket.setSoTimeout(5000);

            // 两端都先创建输出流并刷新头部，再创建输入流。
            try (ObjectOutputStream output =
                         new ObjectOutputStream(socket.getOutputStream())) {

                output.flush();

                try (ObjectInputStream input =
                             new ObjectInputStream(socket.getInputStream())) {

                    input.setObjectInputFilter(RpcServer::filterRequest);

                    Object received = input.readObject();

                    if (!(received instanceof RpcRequest request)) {
                        System.err.println("收到非 RpcRequest 对象，关闭连接");
                        return;
                    }

                    System.out.println(
                            "收到请求：" + request.requestId()
                                    + "，方法：" + request.methodName()
                    );

                    RpcResponse response = requestHandler.handle(request);

                    output.writeObject(response);
                    output.flush();

                    System.out.println(
                            "响应已发送：" + response.requestId()
                                    + "，成功：" + response.success()
                    );
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            System.err.println(
                    "连接处理失败：" + e.getClass().getSimpleName()
                            + "，原因：" + e.getMessage()
            );
        }
    }

    private static ObjectInputFilter.Status filterRequest(
            ObjectInputFilter.FilterInfo info
    ) {
        // 限制对象图的深度、引用数量、已读取字节数和数组长度。
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

        // 数组按照其元素类型判断。
        while (type.isArray()) {
            type = type.getComponentType();
        }

        // 第一版先支持 HelloService 使用的字符串参数。
        if (type.isPrimitive()
                || type == RpcRequest.class
                || type == String.class
                || type == Class.class
                || type == Object.class) {

            return ObjectInputFilter.Status.ALLOWED;
        }

        return ObjectInputFilter.Status.REJECTED;
    }
}