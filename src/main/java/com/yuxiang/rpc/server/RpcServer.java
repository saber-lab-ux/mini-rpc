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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.net.SocketException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class RpcServer implements AutoCloseable {

    private final int port;
    private final RpcRequestHandler requestHandler;
    private final int readTimeoutMillis;
    // 协调启动、接收任务和关闭操作。
    private final Object lifecycleLock = new Object();

    // 记录已接受的连接，包括正在处理和排队等待的连接。
    private final Set<Socket> connections = ConcurrentHashMap.newKeySet();

    private ServerSocket serverSocket;
    private ThreadPoolExecutor executor;

    private volatile boolean closed;
    private boolean started;


    public RpcServer(int port, ServiceRegistry serviceRegistry) {
        this(port, serviceRegistry, 5000);
    }

    public RpcServer(
            int port,
            ServiceRegistry serviceRegistry,
            int readTimeoutMillis
    ) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("端口必须在 1～65535 之间");
        }

        if (readTimeoutMillis <= 0) {
            throw new IllegalArgumentException("读取超时必须大于 0 毫秒");
        }

        this.port = port;
        this.requestHandler = new RpcRequestHandler(serviceRegistry);
        this.readTimeoutMillis = readTimeoutMillis;
    }

    public void start() throws IOException {
        ServerSocket listener;

        synchronized (lifecycleLock) {
            if (closed) {
                throw new IllegalStateException("服务端已经关闭，不能重新启动");
            }

            if (started) {
                throw new IllegalStateException("服务端已经启动");
            }

            InetAddress address = InetAddress.getByName("127.0.0.1");
            listener = new ServerSocket(port, 50, address);

            serverSocket = listener;

            executor = new ThreadPoolExecutor(
                    4,
                    8,
                    60,
                    TimeUnit.SECONDS,
                    new ArrayBlockingQueue<>(32),
                    new ThreadPoolExecutor.AbortPolicy()
            );

            started = true;
        }

        System.out.println(
                "RPC 服务端已启动，监听 127.0.0.1:" + port
        );

        try {
            while (!closed) {
                Socket socket = listener.accept();

                synchronized (lifecycleLock) {
                    // accept 返回时，其他线程可能已经发起关闭。
                    if (closed) {
                        closeConnection(socket);
                        break;
                    }

                    connections.add(socket);

                    try {
                        executor.execute(() -> handleConnection(socket));
                    } catch (RejectedExecutionException e) {
                        System.err.println(
                                "服务端繁忙，拒绝连接："
                                        + socket.getRemoteSocketAddress()
                        );
                        closeConnection(socket);
                    }
                }
            }
        } catch (SocketException e) {
            // 主动关闭监听端口会让 accept 抛出异常，这是正常退出路径。
            if (!closed) {
                throw e;
            }
        } finally {
            close();
        }
    }

    private void handleConnection(Socket socket) {
        try (socket) {
            // 限制读取客户端数据时的阻塞等待时间。
            socket.setSoTimeout(readTimeoutMillis);

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
                            "[" + Thread.currentThread().getName() + "] "
                                    + "收到请求：" + request.requestId()
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
            if (!closed) {
                System.err.println(
                        "连接处理失败：" + e.getClass().getSimpleName()
                                + "，原因：" + e.getMessage()
                );
            }
        } finally {
            connections.remove(socket);
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

    private void closeConnection(Socket socket) {
        try {
            socket.close();
        } catch (IOException e) {
            System.err.println(
                    "关闭连接失败：" + e.getMessage()
            );
        } finally {
            connections.remove(socket);
        }
    }

    @Override
    public void close() {
        ServerSocket listener;
        ThreadPoolExecutor pool;
        Socket[] sockets;

        synchronized (lifecycleLock) {
            // 多次调用 close，不重复执行清理。
            if (closed) {
                return;
            }

            closed = true;

            listener = serverSocket;
            pool = executor;
            sockets = connections.toArray(new Socket[0]);
        }

        // 关闭监听端口，使阻塞中的 accept 返回。
        if (listener != null) {
            try {
                listener.close();
            } catch (IOException e) {
                System.err.println(
                        "关闭监听端口失败：" + e.getMessage()
                );
            }
        }

        // 停止接收任务，移除排队任务，并向工作线程发送中断。
        if (pool != null) {
            pool.shutdownNow();
        }

        // 关闭实际连接，使阻塞中的 Socket 读写能够退出。
        for (Socket socket : sockets) {
            closeConnection(socket);
        }

        if (pool != null) {
            try {
                if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                    System.err.println(
                            "线程池尚未完全退出，可能有业务代码没有响应中断"
                    );
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("等待线程池退出时被中断");
            }
        }

        System.out.println("RPC 服务端关闭流程结束");
    }
}