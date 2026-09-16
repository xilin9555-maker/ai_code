package com.tmz.aicode.config;

import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;

/**
 * 验证缓存管理器的静态策略。测试只构建配置对象，不会连接 Redis。
 */
class RedisCacheManagerConfigTest {

    @Test
    void goodAppCacheUsesFiveMinuteTtlAndRejectsNullValues() {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        RedisCacheManagerConfig config = new RedisCacheManagerConfig(connectionFactory);

        CacheManager cacheManager = config.cacheManager();
        RedisCacheManager redisCacheManager =
                assertInstanceOf(RedisCacheManager.class, cacheManager);
        // 手动构造时补齐 Spring 容器会自动执行的初始化阶段。
        redisCacheManager.initializeCaches();
        RedisCacheConfiguration goodAppConfiguration = redisCacheManager
                .getCacheConfigurations()
                .get(RedisCacheManagerConfig.GOOD_APP_PAGE_CACHE);

        assertEquals(
                Duration.ofMinutes(5),
                goodAppConfiguration.getTtlFunction().getTimeToLive("key", "value")
        );
        assertFalse(goodAppConfiguration.getAllowCacheNullValues());
    }
}
