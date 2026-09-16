package com.tmz.aicode.config;

import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Spring Cache 使用的 Redis 缓存管理器配置。
 *
 * 默认缓存保留 30 分钟；访问频率较高的精选应用列表只保留 5 分钟，以便在减少数据库
 * 查询的同时，让管理员调整精选状态后能够在较短时间内自然刷新。
 */
@Configuration
public class RedisCacheManagerConfig {

    /** 精选应用分页数据使用的缓存区域名称。 */
    public static final String GOOD_APP_PAGE_CACHE = "good_app_page";

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(30);

    private static final Duration GOOD_APP_PAGE_TTL = Duration.ofMinutes(5);

    private final RedisConnectionFactory redisConnectionFactory;

    public RedisCacheManagerConfig(RedisConnectionFactory redisConnectionFactory) {
        this.redisConnectionFactory = redisConnectionFactory;
    }

    /**
     * 创建 Redis 缓存管理器。
     *
     * Key 使用字符串序列化，方便直接在 Redis 中定位缓存项。Value 保留 Spring Data
     * Redis 默认的 JDK 序列化，以完整保存 BaseResponse、Page 及其泛型记录的 Java 类型，
     * 避免无类型 JSON 在缓存命中后无法还原分页对象。
     *
     * @return 具有默认过期时间和精选应用专用过期时间的缓存管理器
     */
    @Bean
    public CacheManager cacheManager() {
        /*
         * 从 Spring Data Redis 的默认缓存配置开始构建。默认配置已经包含缓存名称前缀和
         * Value 序列化器等基础能力，在此基础上只覆盖当前项目需要调整的策略，避免遗漏
         * RedisCacheManager 正常工作所需的默认选项。
         */
        RedisCacheConfiguration defaultConfiguration =
                RedisCacheConfiguration.defaultCacheConfig()
                        // 未单独声明策略的缓存区域统一使用 30 分钟有效期，防止数据长期占用内存。
                        .entryTtl(DEFAULT_TTL)
                        // 业务方法返回 null 时不写入 Redis，避免无效结果长期阻止后续重新查询。
                        .disableCachingNullValues()
                        /*
                         * 缓存 Key 使用字符串序列化器。最终存入 Redis 的 Key 会由缓存区域名称
                         * 和参数哈希共同组成，采用字符串格式便于在 Redis 客户端中直接检索。
                         */
                        .serializeKeysWith(
                                RedisSerializationContext.SerializationPair.fromSerializer(
                                        new StringRedisSerializer()
                                )
                        );

        /*
         * 使用项目已有的 RedisConnectionFactory 创建缓存管理器，使缓存功能与 Session 共用
         * application-local.yml 中的连接地址、端口、数据库编号和认证信息，无需重复创建连接。
         */
        return RedisCacheManager.builder(redisConnectionFactory)
                // 所有未单独配置的缓存区域继承上面定义的 30 分钟默认策略。
                .cacheDefaults(defaultConfiguration)
                /*
                 * 精选应用列表访问频率高，但管理员可能调整精选状态，因此单独缩短为 5 分钟。
                 * 这里基于默认配置派生新配置，Key 序列化和禁止缓存 null 的规则仍会保留。
                 */
                .withCacheConfiguration(
                        GOOD_APP_PAGE_CACHE,
                        defaultConfiguration.entryTtl(GOOD_APP_PAGE_TTL)
                )
                // 根据以上规则创建由 Spring Cache 注解统一使用的 CacheManager。
                .build();
    }
}
