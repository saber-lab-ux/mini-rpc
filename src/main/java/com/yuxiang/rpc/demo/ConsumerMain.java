package com.yuxiang.rpc.demo;

import com.yuxiang.rpc.client.RpcClient;
import com.yuxiang.rpc.model.RpcRequest;
import com.yuxiang.rpc.model.RpcResponse;

import java.io.IOException;
import java.util.UUID;

public class ConsumerMain {

    public static void main(String[] args) {
        RpcClient client = new RpcClient("127.0.0.1", 9000);

        RpcRequest request = new RpcRequest(
                UUID.randomUUID().toString(),
                HelloService.class.getName(),
                "sayHello",
                new Class<?>[]{String.class},
                new Object[]{"张三"}
        );

        try {
            RpcResponse response = client.send(request);

            if (response.success()) {
                System.out.println(
                        "远程调用成功，返回结果：" + response.result()
                );
            } else {
                System.err.println(
                        "远程调用失败：" + response.errorMessage()
                );
            }
        } catch (IOException | ClassNotFoundException e) {
            System.err.println(
                    "RPC 通信失败：" + e.getClass().getSimpleName()
                            + "，原因：" + e.getMessage()
            );
        }
    }
}