package com.yuxiang.rpc.demo;

import com.yuxiang.rpc.client.RpcClient;
import com.yuxiang.rpc.client.RpcException;
import com.yuxiang.rpc.client.RpcProxyFactory;

public class ConsumerMain {

    public static void main(String[] args) {
        RpcClient rpcClient = new RpcClient(
                "127.0.0.1",
                9000,
                3000,
                5000
        );

        RpcProxyFactory proxyFactory =
                new RpcProxyFactory(rpcClient);

        HelloService helloService =
                proxyFactory.createProxy(HelloService.class);

        try {
            String result = helloService.sayHello("张三");

            System.out.println(
                    "远程调用成功，返回结果：" + result
            );
        } catch (RpcException e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
        }
    }
}