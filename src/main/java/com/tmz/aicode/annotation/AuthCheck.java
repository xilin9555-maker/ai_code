package com.tmz.aicode.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个方法在执行前需要进行权限校验。
 *
 * 权限切面会读取 {@link #mustRole()}，判断当前登录用户是否具备要求的角色。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthCheck {

    /**
     * 调用方法所需的角色值。
     *
     * 空字符串表示没有指定特定角色，后续由权限切面按照统一规则处理。
     *
     * @return 所需角色值
     */
    String mustRole() default "";
}
