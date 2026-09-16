package com.tmz.aicode.ratelimit.annotation;

import com.tmz.aicode.ratelimit.enums.RateLimitType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明方法的分布式限流规则。
 *
 * 注解只描述限流参数，具体判断由切面统一完成。这样业务方法不需要直接操作 Redis，
 * 后续调整频率或限流维度时也只需要修改注解配置。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /**
     * 限流 Key 的可选业务前缀，用于隔离使用同一维度的不同业务。
     */
    String key() default "";

    /**
     * 每个时间窗口允许获取的令牌数量。
     */
    int rate() default 10;

    /**
     * 时间窗口长度，单位为秒。
     */
    int rateInterval() default 1;

    /**
     * 限流维度，默认按登录用户隔离。
     */
    RateLimitType limitType() default RateLimitType.USER;

    /**
     * 获取令牌失败时返回给用户的提示。
     */
    String message() default "请求过于频繁，请稍后再试";
}
