package com.tmz.aicode.config;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.Data;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import java.time.Duration;

/**
 * 默认流式对话模型配置。
 *
 * 模型使用 prototype 作用域，每次从 Spring 容器获取时都会创建独立实例。不同应用的
 * 流式请求不会共享底层同步响应读取器，因此可以同时等待和消费各自的模型响应。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "langchain4j.open-ai.streaming-chat-model")
@ConditionalOnProperty(prefix = "langchain4j.open-ai.streaming-chat-model", name = "api-key")
public class StreamingChatModelConfig {

    private String baseUrl;

    private String apiKey;

    private String modelName;

    private Integer maxTokens;

    private Double temperature;

    private Duration timeout = Duration.ofSeconds(120);

    private Boolean logRequests = false;

    private Boolean logResponses = false;

    /**
     * 创建一个新的默认流式模型实例。
     *
     * @return 仅由当前 AI Service 使用的流式模型
     */
    @Bean("streamingChatModelPrototype")
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public StreamingChatModel streamingChatModelPrototype() {
        return OpenAiStreamingChatModel.builder()
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
