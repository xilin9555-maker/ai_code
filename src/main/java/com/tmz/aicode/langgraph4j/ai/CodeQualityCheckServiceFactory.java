package com.tmz.aicode.langgraph4j.ai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 创建代码质量检查 AI 服务。
 *
 * 质量检查属于一次性结构化分析任务，不需要维护对话记忆。每次检查会取得新的路由模型，
 * 使多个工作流可以并发执行质量分析。
 */
@Configuration
public class CodeQualityCheckServiceFactory {

    private final ObjectProvider<ChatModel> routingChatModelProvider;

    public CodeQualityCheckServiceFactory(
            @Qualifier("routingChatModelPrototype")
            ObjectProvider<ChatModel> routingChatModelProvider) {
        this.routingChatModelProvider = routingChatModelProvider;
    }

    /**
     * 创建能够把模型结果映射为 QualityResult 的服务代理。
     *
     * @return 已绑定系统提示词和对话模型的代码质量检查服务
     */
    public CodeQualityCheckService createCodeQualityCheckService() {
        ChatModel chatModel = routingChatModelProvider.getObject();
        return AiServices.builder(CodeQualityCheckService.class)
                .chatModel(chatModel)
                .build();
    }

    /** 保留默认 Bean，正式工作流会通过工厂方法为每次检查创建独立服务。 */
    @Bean
    public CodeQualityCheckService codeQualityCheckService() {
        return createCodeQualityCheckService();
    }
}
