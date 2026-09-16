package com.tmz.aicode.ratelimit.aspect;

import com.tmz.aicode.exception.BusinessException;
import com.tmz.aicode.exception.ErrorCode;
import com.tmz.aicode.model.entity.User;
import com.tmz.aicode.ratelimit.annotation.RateLimit;
import com.tmz.aicode.ratelimit.enums.RateLimitType;
import com.tmz.aicode.service.UserService;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 分布式限流切面的纯单元测试。
 *
 * Redis 客户端和令牌桶均使用模拟对象，测试只验证限流规则、Key 生成和异常语义，
 * 不会连接真实 Redis，也不会触发模型或其他外部服务。
 */
class RateLimitAspectTest {

    private static final Duration EXPECTED_TTL = Duration.ofHours(1);

    /** 每个测试结束后清理当前线程绑定的请求，避免影响同一线程中的其他测试。 */
    @AfterEach
    void resetRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    /**
     * 已登录请求应使用用户 id 构造独立令牌桶，并按注解声明的窗口申请令牌。
     */
    @Test
    void userLimitUsesLoginUserIdAndAcquiresToken() throws Exception {
        MockHttpServletRequest request = bindRequest();
        RedissonClient redissonClient = mock(RedissonClient.class);
        RRateLimiter rateLimiter = mock(RRateLimiter.class);
        UserService userService = mock(UserService.class);
        JoinPoint joinPoint = mock(JoinPoint.class);
        RateLimit rule = getRule("userEndpoint");

        when(userService.getLoginUser(request)).thenReturn(User.builder().id(1001L).build());
        when(redissonClient.getRateLimiter("rate_limit:user:1001")).thenReturn(rateLimiter);
        when(rateLimiter.tryAcquire(1)).thenReturn(true);

        new RateLimitAspect(redissonClient, userService).doBefore(joinPoint, rule);

        verify(rateLimiter).trySetRate(
                RateType.OVERALL,
                5,
                60,
                RateIntervalUnit.SECONDS
        );
        verify(rateLimiter).expire(EXPECTED_TTL);
        verify(rateLimiter).tryAcquire(1);
    }

    /**
     * 令牌已经耗尽时必须中止业务调用，并保留注解中面向用户的提示文本。
     */
    @Test
    void exhaustedBucketThrowsTooManyRequest() throws Exception {
        MockHttpServletRequest request = bindRequest();
        RedissonClient redissonClient = mock(RedissonClient.class);
        RRateLimiter rateLimiter = mock(RRateLimiter.class);
        UserService userService = mock(UserService.class);
        RateLimit rule = getRule("userEndpoint");

        when(userService.getLoginUser(request)).thenReturn(User.builder().id(1001L).build());
        when(redissonClient.getRateLimiter("rate_limit:user:1001")).thenReturn(rateLimiter);
        when(rateLimiter.tryAcquire(1)).thenReturn(false);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new RateLimitAspect(redissonClient, userService)
                        .doBefore(mock(JoinPoint.class), rule)
        );

        assertEquals(ErrorCode.TOO_MANY_REQUEST.getCode(), exception.getCode());
        assertEquals("AI 对话请求过于频繁，请稍后再试", exception.getMessage());
    }

    /**
     * 无登录状态时仍需要保护接口，因此 USER 规则应退化为客户端 IP 规则。
     * 多级代理地址只取最左侧的原始客户端地址。
     */
    @Test
    void userLimitFallsBackToFirstForwardedIp() throws Exception {
        MockHttpServletRequest request = bindRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.10, 10.0.0.1");
        RedissonClient redissonClient = mock(RedissonClient.class);
        RRateLimiter rateLimiter = mock(RRateLimiter.class);
        UserService userService = mock(UserService.class);

        when(userService.getLoginUser(request))
                .thenThrow(new BusinessException(ErrorCode.NOT_LOGIN_ERROR));
        when(redissonClient.getRateLimiter("rate_limit:ip:203.0.113.10"))
                .thenReturn(rateLimiter);
        when(rateLimiter.tryAcquire(1)).thenReturn(true);

        new RateLimitAspect(redissonClient, userService).doBefore(
                mock(JoinPoint.class),
                getRule("userEndpoint")
        );

        verify(redissonClient).getRateLimiter("rate_limit:ip:203.0.113.10");
        verify(rateLimiter).tryAcquire(1);
    }

    /** API 规则应以声明类和方法名区分不同接口，避免多个接口共用同一令牌桶。 */
    @Test
    void apiLimitUsesDeclaringClassAndMethodName() throws Exception {
        bindRequest();
        RedissonClient redissonClient = mock(RedissonClient.class);
        RRateLimiter rateLimiter = mock(RRateLimiter.class);
        JoinPoint joinPoint = mock(JoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        Method endpointMethod = RateLimitedMethods.class.getDeclaredMethod("apiEndpoint");

        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(endpointMethod);
        when(redissonClient.getRateLimiter(
                "rate_limit:generation:api:RateLimitedMethods.apiEndpoint"
        )).thenReturn(rateLimiter);
        when(rateLimiter.tryAcquire(1)).thenReturn(true);

        new RateLimitAspect(redissonClient, mock(UserService.class)).doBefore(
                joinPoint,
                endpointMethod.getAnnotation(RateLimit.class)
        );

        verify(redissonClient).getRateLimiter(
                "rate_limit:generation:api:RateLimitedMethods.apiEndpoint"
        );
    }

    /** 将模拟请求绑定到当前线程，复现控制器进入切面时的请求上下文。 */
    private MockHttpServletRequest bindRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(request, response)
        );
        return request;
    }

    /** 从测试方法声明上读取真实注解，避免手工模拟注解遗漏默认值。 */
    private RateLimit getRule(String methodName) throws NoSuchMethodException {
        Method method = RateLimitedMethods.class.getDeclaredMethod(methodName);
        return method.getAnnotation(RateLimit.class);
    }

    /** 提供不同维度的注解样本，仅用于切面单元测试。 */
    private static class RateLimitedMethods {

        @RateLimit(
                limitType = RateLimitType.USER,
                rate = 5,
                rateInterval = 60,
                message = "AI 对话请求过于频繁，请稍后再试"
        )
        void userEndpoint() {
        }

        @RateLimit(key = "generation", limitType = RateLimitType.API)
        void apiEndpoint() {
        }
    }
}
