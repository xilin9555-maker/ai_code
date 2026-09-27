package com.tmz.aicode;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * 用户服务启动入口。
 *
 * 用户接口、登录会话和用户数据访问都由该进程独立承载。
 */
@SpringBootApplication
@EnableDubbo
@MapperScan("com.tmz.aicode.mapper")
@EnableAspectJAutoProxy(exposeProxy = true)
public class AiCodeUserApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiCodeUserApplication.class, args);
    }
}
