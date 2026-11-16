package com.yuxiang.rpc.demo;

public class HelloServiceImpl implements HelloService {

    @Override
    public String sayHello(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("姓名不能为空");
        }

        return "你好，" + name;
    }
}