package com.tmz.aicode.config;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 统一配置接口返回数据的 JSON 序列化方式。
 */
@Configuration
public class JacksonConfig {

    /**
     * 将 Long 类型的数字序列化成字符串。
     *
     * 用户 id 使用雪花算法生成，数值通常会超过 JavaScript 能够安全表示的整数范围。
     * 转成字符串返回可以完整保留每一位数字，避免前端把错误的 id 带回查询、更新或
     * 删除接口。这里同时处理包装类型 Long 和基本类型 long，保证所有长整型字段行为一致。
     *
     * @return 应用于 Spring MVC 全局 ObjectMapper 的定制配置
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer longToStringCustomizer() {
        return builder -> builder
                .serializerByType(Long.class, ToStringSerializer.instance)
                .serializerByType(Long.TYPE, ToStringSerializer.instance);
    }
}
