package com.tmz.aicode.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.Data;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import java.time.Duration;

/**
 * 智能路由和工作流短分析任务使用的模型配置。
 *
 * 非流式模型同样采用 prototype 作用域，使并发创建应用、规划图片和检查代码时分别使用
 * 独立客户端，避免一个较慢的请求占住其他用户的模型调用。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "langchain4j.open-ai.routing-chat-model")
@ConditionalOnProperty(prefix = "langchain4j.open-ai.routing-chat-model", name = "api-key")
public class RoutingAiModelConfig {

    private String baseUrl;

    private String apiKey;

    private String modelName;

    private Integer maxTokens;

    private Double temperature;

    private Duration timeout = Duration.ofSeconds(120);

    private Boolean logRequests = false;

    private Boolean logResponses = false;

    /**
     * 创建一个新的路由模型实例。
     *
     * @return 仅由当前短分析任务使用的非流式模型
     */
    @Bean("routingChatModelPrototype")
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public ChatModel routingChatModelPrototype() {
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .maxTokens(maxTokens)
                .temperature(temperature)
                .timeout(timeout)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .build();
    }
}
