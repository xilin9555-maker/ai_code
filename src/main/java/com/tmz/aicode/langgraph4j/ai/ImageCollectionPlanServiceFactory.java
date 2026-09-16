package com.tmz.aicode.langgraph4j.ai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 创建图片收集规划 AI 服务。
 *
 * 图片规划是一次结构化分析任务，不注册图片工具，也不维护独立会话记忆。每次规划使用
 * 新的路由模型，生成计划后由工作节点直接调用 Java 工具执行任务。
 */
@Configuration
public class ImageCollectionPlanServiceFactory {

    private final ObjectProvider<ChatModel> routingChatModelProvider;

    public ImageCollectionPlanServiceFactory(
            @Qualifier("routingChatModelPrototype")
            ObjectProvider<ChatModel> routingChatModelProvider) {
        this.routingChatModelProvider = routingChatModelProvider;
    }

    /**
     * 创建能够把模型 JSON 映射为 ImageCollectionPlan 的服务代理。
     *
     * @return 已绑定规划提示词和对话模型的图片收集规划服务
     */
    public ImageCollectionPlanService createImageCollectionPlanService() {
        ChatModel chatModel = routingChatModelProvider.getObject();
        return AiServices.builder(ImageCollectionPlanService.class)
                .chatModel(chatModel)
                .build();
    }

    /** 保留默认 Bean，正式工作流会通过工厂方法为每次规划创建独立服务。 */
    @Bean
    public ImageCollectionPlanService imageCollectionPlanService() {
        return createImageCollectionPlanService();
    }
}
