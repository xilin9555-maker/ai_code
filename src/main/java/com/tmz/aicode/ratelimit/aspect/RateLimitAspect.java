package com.tmz.aicode.ratelimit.aspect;

import com.tmz.aicode.exception.BusinessException;
import com.tmz.aicode.exception.ErrorCode;
import com.tmz.aicode.model.entity.User;
import com.tmz.aicode.ratelimit.annotation.RateLimit;
import com.tmz.aicode.ratelimit.enums.RateLimitType;
import com.tmz.aicode.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.Duration;

/**
 * 执行 {@link RateLimit} 规则的切面。
 *
 * 切面在业务方法执行前生成稳定的 Redis Key，并从 Redisson 分布式令牌桶获取一个令牌。
 * 所有应用实例访问同一个令牌桶，因此横向扩容后仍能维持统一的请求上限。
 */
@Aspect
@Component
@Slf4j
public class RateLimitAspect {

    /** 限流数据的统一 Key 前缀，便于在 Redis 中识别和运维。 */
    private static final String RATE_LIMIT_KEY_PREFIX = "rate_limit:";

    /** 限流器长期无人访问后自动回收，避免 Redis 残留大量历史用户 Key。 */
    private static final Duration RATE_LIMITER_TTL = Duration.ofHours(1);

    private final RedissonClient redissonClient;

    private final UserService userService;

    public RateLimitAspect(RedissonClient redissonClient, UserService userService) {
        this.redissonClient = redissonClient;
        this.userService = userService;
    }

    /**
     * 在目标方法执行前完成令牌获取。
     *
     * trySetRate 只会在限流器尚未初始化时写入规则，不会在每次请求中重置令牌状态。
     * 初始化之后再刷新过期时间，确保首次创建的 Redis 数据同样具备 TTL。
     *
     * @param point 当前被拦截的方法调用
     * @param rateLimit 方法声明的限流规则
     */
    @Before("@annotation(rateLimit)")
    public void doBefore(JoinPoint point, RateLimit rateLimit) {
        validateRule(rateLimit);
        String limiterKey = generateRateLimitKey(point, rateLimit);
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(limiterKey);

        rateLimiter.trySetRate(
                RateType.OVERALL,
                rateLimit.rate(),
                rateLimit.rateInterval(),
                RateIntervalUnit.SECONDS
        );
        rateLimiter.expire(RATE_LIMITER_TTL);

        if (!rateLimiter.tryAcquire(1)) {
            log.warn("请求触发限流，key={}，rate={}，interval={}s",
                    limiterKey, rateLimit.rate(), rateLimit.rateInterval());
            throw new BusinessException(
                    ErrorCode.TOO_MANY_REQUEST,
                    rateLimit.message()
            );
        }
    }

    /**
     * 检查注解参数，避免错误配置创建永远不可用或无法初始化的限流器。
     */
    private void validateRule(RateLimit rateLimit) {
        if (rateLimit.rate() <= 0 || rateLimit.rateInterval() <= 0) {
            throw new BusinessException(
                    ErrorCode.SYSTEM_ERROR,
                    "限流配置必须大于 0"
            );
        }
    }

    /**
     * 根据注解选择的维度生成分布式限流 Key。
     *
     * API 维度使用控制器类名和方法名；USER 维度优先使用登录用户 id，获取登录状态失败
     * 时降级为客户端 IP；IP 维度始终使用客户端地址。可选业务前缀用于进一步隔离令牌桶。
     */
    private String generateRateLimitKey(JoinPoint point, RateLimit rateLimit) {
        StringBuilder keyBuilder = new StringBuilder(RATE_LIMIT_KEY_PREFIX);
        if (rateLimit.key() != null && !rateLimit.key().isBlank()) {
            keyBuilder.append(rateLimit.key().trim()).append(':');
        }

        RateLimitType limitType = rateLimit.limitType();
        if (limitType == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "限流类型不能为空");
        }
        switch (limitType) {
            case API -> appendApiKey(keyBuilder, point);
            case USER -> appendUserOrIpKey(keyBuilder);
            case IP -> keyBuilder.append("ip:").append(getClientIp());
            default -> throw new BusinessException(
                    ErrorCode.SYSTEM_ERROR,
                    "不支持的限流类型"
            );
        }
        return keyBuilder.toString();
    }

    /** 使用声明方法的完整业务标识生成接口级 Key。 */
    private void appendApiKey(StringBuilder keyBuilder, JoinPoint point) {
        if (!(point.getSignature() instanceof MethodSignature signature)) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "无法识别限流方法");
        }
        Method method = signature.getMethod();
        keyBuilder.append("api:")
                .append(method.getDeclaringClass().getSimpleName())
                .append('.')
                .append(method.getName());
    }

    /**
     * 登录用户按用户 id 限流；请求没有有效登录状态时退化为 IP 限流。
     */
    private void appendUserOrIpKey(StringBuilder keyBuilder) {
        HttpServletRequest request = getCurrentRequest();
        if (request != null) {
            try {
                User loginUser = userService.getLoginUser(request);
                if (loginUser != null && loginUser.getId() != null) {
                    keyBuilder.append("user:").append(loginUser.getId());
                    return;
                }
            } catch (BusinessException ignored) {
                // 没有登录状态时仍需保护接口，继续使用客户端地址构造 Key。
            }
        }
        keyBuilder.append("ip:").append(getClientIp());
    }

    /**
     * 获取客户端地址，并兼容常见反向代理头。
     *
     * X-Forwarded-For 可能包含多级代理地址，最左侧是最初客户端。部署时应由可信反向
     * 代理覆盖这些请求头，避免客户端自行伪造地址绕过 IP 维度限制。
     */
    private String getClientIp() {
        HttpServletRequest request = getCurrentRequest();
        if (request == null) {
            return "unknown";
        }

        String clientIp = request.getHeader("X-Forwarded-For");
        if (isUnknown(clientIp)) {
            clientIp = request.getHeader("X-Real-IP");
        }
        if (isUnknown(clientIp)) {
            clientIp = request.getRemoteAddr();
        }
        if (clientIp != null && clientIp.contains(",")) {
            clientIp = clientIp.split(",", 2)[0].trim();
        }
        return isUnknown(clientIp) ? "unknown" : clientIp.trim();
    }

    /** 从当前请求线程中读取 Servlet 请求；非 Web 调用返回 null。 */
    private HttpServletRequest getCurrentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            return servletAttributes.getRequest();
        }
        return null;
    }

    /** 判断地址是否为空或代理使用的 unknown 占位值。 */
    private boolean isUnknown(String value) {
        return value == null || value.isBlank() || "unknown".equalsIgnoreCase(value);
    }
}
