package com.tmz.aicode;

import dev.langchain4j.community.store.embedding.redis.spring.RedisEmbeddingStoreAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

// 当前项目只使用 Redis 保存对话记忆，不需要初始化向量存储及其相关组件。
@SpringBootApplication(exclude = RedisEmbeddingStoreAutoConfiguration.class)
@MapperScan("com.tmz.aicode.mapper")
@EnableAspectJAutoProxy(exposeProxy = true)
public class AiCodeApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiCodeApplication.class, args);
    }

}
