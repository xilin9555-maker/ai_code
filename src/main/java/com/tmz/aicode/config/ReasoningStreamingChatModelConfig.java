package com.tmz.aicode.config;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 工程项目生成使用的推理流式模型配置。
 *
 * 连接地址和密钥沿用普通对话模型的配置，避免在多个位置重复维护敏感信息。
 * 这个 Bean 拥有独立名称，后续可以只把复杂的工程生成请求交给它处理，普通网页生成
 * 仍然使用响应更快的默认流式模型。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "langchain4j.open-ai.chat-model")
@ConditionalOnProperty(prefix = "langchain4j.open-ai.chat-model", name = "api-key")
public class ReasoningStreamingChatModelConfig {

    /**
     * OpenAI 兼容接口地址，例如 DeepSeek 的 API 地址。
     */
    private String baseUrl;

    /**
     * 调用模型所需的密钥，只从本地配置或部署环境读取。
     */
    private String apiKey;

    /**
     * 单次模型请求的最长等待时间，默认给工程生成保留两分钟。
     */
    private Duration timeout = Duration.ofSeconds(120);

    /**
     * 创建专门用于工程项目生成的流式模型。
     *
     * 开发阶段先使用 deepseek-chat，首段内容返回更快，便于反复调试文件工具。
     * 准备在正式环境启用深度推理时，可以把 modelName 改为 deepseek-reasoner，
     * 并把 maxTokens 调整为 32768，让模型有足够空间规划和生成完整工程。
     *
     * @return 名为 reasoningStreamingChatModel 的流式模型 Bean
     */
    @Bean
    public StreamingChatModel reasoningStreamingChatModel() {
        final String modelName = "deepseek-chat";
        final int maxTokens = 8192;
        return OpenAiStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .maxTokens(maxTokens)
                .timeout(timeout)
                .logRequests(true)
                .logResponses(true)
                .build();
    }
}
