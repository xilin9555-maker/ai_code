package com.tmz.aicode.langgraph4j.ai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 创建代码质量检查 AI 服务。
 *
 * 质量检查属于一次性结构化分析任务，因此复用项目现有的非流式对话模型即可，不需要
 * 建立流式响应或单独维护对话记忆。
 */
@Slf4j
@Configuration
public class CodeQualityCheckServiceFactory {

    /** 项目统一配置的非流式对话模型。 */
    @Resource
    private ChatModel chatModel;

    /**
     * 创建能够把模型结果映射为 QualityResult 的服务代理。
     *
     * @return 已绑定系统提示词和对话模型的代码质量检查服务
     */
    @Bean
    public CodeQualityCheckService createCodeQualityCheckService() {
        return AiServices.builder(CodeQualityCheckService.class)
                .chatModel(chatModel)
                .build();
    }
}
