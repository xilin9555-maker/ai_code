package com.tmz.aicode.langgraph4j.ai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 创建图片收集规划 AI 服务。
 *
 * 图片规划是一次结构化分析任务，因此复用现有非流式对话模型，不注册图片工具，也不维护
 * 独立会话记忆。模型生成计划后，工作节点会直接调用 Java 工具执行任务。
 */
@Configuration
public class ImageCollectionPlanServiceFactory {

    /** 项目统一配置的非流式对话模型。 */
    @Resource
    private ChatModel chatModel;

    /**
     * 创建能够把模型 JSON 映射为 ImageCollectionPlan 的服务代理。
     *
     * @return 已绑定规划提示词和对话模型的图片收集规划服务
     */
    @Bean
    public ImageCollectionPlanService createImageCollectionPlanService() {
        return AiServices.builder(ImageCollectionPlanService.class)
                .chatModel(chatModel)
                .build();
    }
}
