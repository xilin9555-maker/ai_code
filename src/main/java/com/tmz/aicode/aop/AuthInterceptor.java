package com.tmz.aicode.aop;

import com.tmz.aicode.annotation.AuthCheck;
import com.tmz.aicode.exception.BusinessException;
import com.tmz.aicode.exception.ErrorCode;
import com.tmz.aicode.model.entity.User;
import com.tmz.aicode.model.enums.UserRoleEnum;
import com.tmz.aicode.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 统一处理标记了 {@link AuthCheck} 的方法权限。
 *
 * 切面会在目标方法执行前确认用户已经登录，并根据注解配置检查用户角色。
 */
@Aspect
@Component
public class AuthInterceptor {

    @Resource
    private UserService userService;

    /**
     * 在目标方法前后执行权限检查。
     *
     * @param joinPoint 当前被拦截的方法
     * @param authCheck 方法上的权限配置
     * @return 目标方法原本的返回结果
     * @throws Throwable 目标方法执行时产生的异常
     */
    @Around("@annotation(authCheck)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {
        String mustRole = authCheck.mustRole();

        // 从当前线程绑定的请求上下文中取得这一次 HTTP 请求。
        RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();

        // getLoginUser 会同时完成登录状态检查，并返回数据库中的最新用户信息。
        User loginUser = userService.getLoginUser(request);
        UserRoleEnum mustRoleEnum = UserRoleEnum.getEnumByValue(mustRole);

        // 没有指定具体角色时，只要用户已经登录就可以继续执行目标方法。
        if (mustRoleEnum == null) {
            return joinPoint.proceed();
        }

        UserRoleEnum userRoleEnum = UserRoleEnum.getEnumByValue(loginUser.getUserRole());
        if (userRoleEnum == null) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }

        // 需要管理员权限的方法只允许管理员继续执行。
        if (UserRoleEnum.ADMIN.equals(mustRoleEnum)
                && !UserRoleEnum.ADMIN.equals(userRoleEnum)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }

        return joinPoint.proceed();
    }
}
