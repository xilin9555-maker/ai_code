package com.tmz.aicode;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication
@MapperScan("com.tmz.aicode.mapper")
@EnableAspectJAutoProxy(exposeProxy = true)
public class AiCodeApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiCodeApplication.class, args);
    }

}
