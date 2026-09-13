package com.tmz.aicode.config;

import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redis 对话记忆存储配置。
 *
 * 这里把配置文件中的连接地址、端口、密码和过期时间集中绑定到一个对象，再创建
 * RedisChatMemoryStore。后续 AI 服务只需要注入这个 Bean，不必重复关心 Redis 的连接细节。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "spring.data.redis")
public class RedisChatMemoryStoreConfig {

    /**
     * Redis 服务地址，本地开发环境通常使用 localhost。
     */
    private String host;

    /**
     * Redis 服务端口，默认端口为 6379。
     */
    private int port;

    /**
     * Redis 访问密码。本地 Redis 未设置密码时保持为空即可。
     */
    private String password;

    /**
     * 对话记忆的存活时间，单位为秒。过期后 Redis 会自动回收对应数据，避免长期占用内存。
     */
    private long ttl;

    /**
     * 创建供 LangChain4j 保存和读取对话记忆的 Redis 存储组件。
     *
     * @return 使用当前连接参数和过期时间构建的对话记忆存储
     */
    @Bean
    public RedisChatMemoryStore redisChatMemoryStore() {
        return RedisChatMemoryStore.builder()
                .host(host)
                .port(port)
                .password(password)
                .ttl(ttl)
                .build();
    }
}
