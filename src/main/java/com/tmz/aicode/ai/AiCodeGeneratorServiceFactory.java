package com.tmz.aicode.ai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 创建网页代码生成服务的 Spring 配置。
 *
 * ChatModel 由 LangChain4j 的 Spring Boot Starter 根据本地模型配置创建，这个工厂
 * 再把模型与 AiCodeGeneratorService 接口连接起来。项目的其他组件可以直接注入
 * AiCodeGeneratorService，不需要了解模型客户端的初始化细节。
 */
@Configuration
@ConditionalOnProperty(prefix = "langchain4j.open-ai.chat-model", name = "api-key")
public class AiCodeGeneratorServiceFactory {

    private final ChatModel chatModel;
    private final StreamingChatModel streamingChatModel;

    /**
     * 使用构造器接收统一的对话模型，依赖关系清晰，也便于在测试中替换为模拟模型。
     *
     * @param chatModel 已完成地址、密钥和模型名称配置的普通对话模型
     * @param streamingChatModel 可以逐段返回生成内容的流式对话模型
     */
    public AiCodeGeneratorServiceFactory(ChatModel chatModel, StreamingChatModel streamingChatModel) {
        this.chatModel = chatModel;
        this.streamingChatModel = streamingChatModel;
    }

    /**
     * 为 AI 服务接口创建代理对象并交给 Spring 容器管理。
     *
     * 当接口方法被调用时，代理对象会读取方法上的 SystemMessage 注解，从 resources
     * 中加载对应提示词，然后把模型返回的 JSON 转换为接口方法声明的结构化结果类型。
     *
     * @return 可以直接注入业务类使用的网页代码生成服务
     */
    @Bean
    public AiCodeGeneratorService aiCodeGeneratorService() {
        return AiServices.builder(AiCodeGeneratorService.class)
                .chatModel(chatModel)
                .streamingChatModel(streamingChatModel)
                .build();
    }
}
