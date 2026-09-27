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
 * 工程项目生成使用的推理流式模型配置。
 *
 * 该配置拥有独立属性前缀，可以为复杂工程选择更大的输出上限或专用推理模型。Bean 使用
 * prototype 作用域，确保并发工程生成不会共享同一个底层流式响应读取器。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "langchain4j.open-ai.reasoning-streaming-chat-model")
@ConditionalOnProperty(
        prefix = "langchain4j.open-ai.reasoning-streaming-chat-model",
        name = "api-key"
)
public class ReasoningStreamingChatModelConfig {

    /**
     * OpenAI 兼容接口地址，例如 DeepSeek 的 API 地址。
     */
    private String baseUrl;

    /**
     * 调用模型所需的密钥，只从本地配置或部署环境读取。
     */
    private String apiKey;

    /** 工程生成使用的模型名称。 */
    private String modelName;

    /** 单次生成允许返回的最大 token 数。 */
    private Integer maxTokens;

    /** 较低温度可以让文件结构和工具调用更稳定。 */
    private Double temperature;

    /**
     * 单次模型请求的最长等待时间，默认给工程生成保留两分钟。
     */
    private Duration timeout = Duration.ofSeconds(120);

    private Boolean logRequests = false;

    private Boolean logResponses = false;

    /**
     * 创建专门用于工程项目生成的流式模型。
     *
     * @return 仅由当前工程生成服务使用的流式模型
     */
    @Bean("reasoningStreamingChatModelPrototype")
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    public StreamingChatModel reasoningStreamingChatModelPrototype() {
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

