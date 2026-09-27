package com.tmz.aicode;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 截图服务启动入口。
 *
 * 截图和对象存储上传是资源密集型操作，独立部署后不会阻塞应用生成请求。
 */
@SpringBootApplication
@EnableDubbo
public class AiCodeScreenshotApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiCodeScreenshotApplication.class, args);
    }
}
