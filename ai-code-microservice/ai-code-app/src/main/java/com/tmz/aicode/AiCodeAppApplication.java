package com.tmz.aicode;

import dev.langchain4j.community.store.embedding.redis.spring.RedisEmbeddingStoreAutoConfiguration;
import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * 应用服务启动入口。
 *
 * 该服务负责应用管理、代码生成、工作流编排、部署和对话历史。
 */
@SpringBootApplication(exclude = RedisEmbeddingStoreAutoConfiguration.class)
@EnableDubbo
@MapperScan("com.tmz.aicode.mapper")
@EnableAspectJAutoProxy(exposeProxy = true)
@EnableCaching
public class AiCodeAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiCodeAppApplication.class, args);
    }
}
