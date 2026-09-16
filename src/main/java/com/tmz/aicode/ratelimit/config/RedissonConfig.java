package com.tmz.aicode.ratelimit.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 单节点客户端配置。
 *
 * 客户端复用项目现有的 Redis 地址、数据库和密码配置。限流器、登录会话、对话记忆和
 * 业务缓存虽然使用同一个 Redis 服务，但各自采用不同 Key 前缀，不会互相覆盖。
 */
@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${spring.data.redis.database:0}")
    private int redisDatabase;

    /**
     * 创建应用内唯一的 Redisson 客户端。
     *
     * 连接池保持较小规模，因为当前仅使用轻量的令牌获取命令。连接和命令超时能够避免
     * Redis 异常时请求线程无限等待；Spring 容器关闭时会自动调用 shutdown 释放资源。
     *
     * @return 连接当前 Redis 单节点的客户端
     */
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        String address = "redis://" + redisHost + ":" + redisPort;
        SingleServerConfig singleServerConfig = config.useSingleServer()
                .setAddress(address)
                .setDatabase(redisDatabase)
                .setConnectionMinimumIdleSize(1)
                .setConnectionPoolSize(10)
                .setIdleConnectionTimeout(30_000)
                .setConnectTimeout(5_000)
                .setTimeout(3_000)
                .setRetryAttempts(3)
                .setRetryInterval(1_500);

        // 本地 Redis 可以不设置密码，只有存在有效配置时才发送 AUTH 命令。
        if (redisPassword != null && !redisPassword.isBlank()) {
            singleServerConfig.setPassword(redisPassword);
        }
        return Redisson.create(config);
    }
}
