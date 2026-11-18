package com.yuxiang.rpc;

import com.yuxiang.rpc.client.RpcClient;
import com.yuxiang.rpc.client.RpcProxyFactory;
import com.yuxiang.rpc.demo.HelloService;
import com.yuxiang.rpc.server.RpcServer;
import com.yuxiang.rpc.server.ServiceRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class RpcConcurrencyCheck {

    public static void main(String[] args) throws Exception {
        AtomicInteger activeCalls = new AtomicInteger();
        AtomicInteger maxActiveCalls = new AtomicInteger();

        // 等待 4 个业务调用都进入，确认它们确实重叠执行。
        CountDownLatch businessEntered = new CountDownLatch(4);

        ServiceRegistry registry = new ServiceRegistry();

        registry.register(HelloService.class, name -> {
            int active = activeCalls.incrementAndGet();
            maxActiveCalls.accumulateAndGet(active, Math::max);
            businessEntered.countDown();

            try {
                if (!businessEntered.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException(
                            "未能在规定时间内同时进入 4 个业务调用"
                    );
                }

                return "你好，" + name;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("业务调用被中断", e);
            } finally {
                activeCalls.decrementAndGet();
            }
        });

        RpcServer server = new RpcServer(9001, registry, 10000);

        ExecutorService serverThread =
                Executors.newSingleThreadExecutor();

        ExecutorService clients =
                Executors.newFixedThreadPool(4);

        Future<?> serverTask = serverThread.submit(() -> {
            server.start();
            return null;
        });

        try {
            System.out.println(
                    "看到“监听 127.0.0.1:9001”后，"
                            + "点击本控制台并按回车，开始并发调用。"
            );

            System.in.read();

            if (serverTask.isDone()) {
                serverTask.get();
                throw new IllegalStateException("服务端已经退出");
            }

            RpcClient rpcClient = new RpcClient(
                    "127.0.0.1", 9001, 3000, 10000
            );

            HelloService service =
                    new RpcProxyFactory(rpcClient)
                            .createProxy(HelloService.class);

            CountDownLatch ready = new CountDownLatch(4);
            CountDownLatch start = new CountDownLatch(1);

            List<Future<String>> results = new ArrayList<>();

            for (int i = 1; i <= 4; i++) {
                String name = "用户" + i;

                results.add(clients.submit(() -> {
                    ready.countDown();

                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("等待开始信号超时");
                    }

                    return service.sayHello(name);
                }));
            }

            if (!ready.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("客户端任务准备超时");
            }

            start.countDown();

            for (int i = 0; i < results.size(); i++) {
                String actual = results.get(i).get(15, TimeUnit.SECONDS);
                String expected = "你好，用户" + (i + 1);

                if (!expected.equals(actual)) {
                    throw new IllegalStateException(
                            "结果不匹配，预期：" + expected
                                    + "，实际：" + actual
                    );
                }

                System.out.println(actual);
            }

            System.out.println(
                    "最大同时执行业务数：" + maxActiveCalls.get()
            );

            if (maxActiveCalls.get() != 4) {
                throw new IllegalStateException("并发验证未通过");
            }

            System.out.println("并发验证通过：4 个请求重叠执行且结果正确");

        } finally {
            clients.shutdownNow();
            server.close();
            serverThread.shutdownNow();

            clients.awaitTermination(5, TimeUnit.SECONDS);
            serverThread.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}