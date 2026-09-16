package com.tmz.aicode.utils;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Spring 上下文工具类。
 *
 * 工作流节点通过静态工厂方法创建，不由 Spring 直接管理实例，因此无法在节点对象中使用
 * 常规依赖注入。该工具在应用启动时保存 ApplicationContext，使静态节点动作能够按类型
 * 或名称获取已经由 Spring 管理的服务对象。
 */
@Component
public class SpringContextUtil implements ApplicationContextAware {

    /** Spring 应用启动后注入的全局上下文。 */
    private static ApplicationContext applicationContext;

    /**
     * 保存 Spring 创建的应用上下文。
     *
     * @param applicationContext 当前应用的 Spring 上下文
     * @throws BeansException Spring 无法设置上下文时抛出
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext)
            throws BeansException {
        SpringContextUtil.applicationContext = applicationContext;
    }

    /**
     * 根据 Bean 类型获取唯一实例。
     *
     * @param clazz Bean 类型
     * @param <T> Bean 的实际类型
     * @return Spring 容器中与类型匹配的 Bean
     */
    public static <T> T getBean(Class<T> clazz) {
        return applicationContext.getBean(clazz);
    }

    /**
     * 根据 Bean 名称获取实例。
     *
     * @param name Bean 名称
     * @return Spring 容器中与名称匹配的 Bean
     */
    public static Object getBean(String name) {
        return applicationContext.getBean(name);
    }

    /**
     * 根据 Bean 名称和类型获取实例。
     *
     * @param name Bean 名称
     * @param clazz Bean 类型
     * @param <T> Bean 的实际类型
     * @return Spring 容器中同时匹配名称和类型的 Bean
     */
    public static <T> T getBean(String name, Class<T> clazz) {
        return applicationContext.getBean(name, clazz);
    }
}
