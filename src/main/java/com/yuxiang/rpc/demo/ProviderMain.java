package com.yuxiang.rpc.demo;

import com.yuxiang.rpc.server.RpcServer;
import com.yuxiang.rpc.server.ServiceRegistry;

import java.io.IOException;

public class ProviderMain {

    public static void main(String[] args) throws IOException {
        ServiceRegistry registry = new ServiceRegistry();

        registry.register(
                HelloService.class,
                new HelloServiceImpl()
        );

        RpcServer server = new RpcServer(9000, registry);

        server.start();
    }
}