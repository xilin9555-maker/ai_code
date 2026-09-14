package com.tmz.aicode.ai;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 创建代码生成类型路由服务。
 *
 * 分类任务只需要一次简短的非流式响应，因此直接复用已经配置好的普通 ChatModel。
 * 后续如果需要使用成本更低的专用模型，只需在这里替换注入的模型，不会影响应用服务。
 */
@Configuration
@ConditionalOnProperty(prefix = "langchain4j.open-ai.chat-model", name = "api-key")
public class AiCodeGenTypeRoutingServiceFactory {

    private final ChatModel chatModel;

    /**
     * @param chatModel 由模型配置创建的普通对话模型
     */
    public AiCodeGenTypeRoutingServiceFactory(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * 创建由 LangChain4j 实现的路由代理。
     *
     * @return 可以把用户需求直接分类为 CodeGenTypeEnum 的服务
     */
    @Bean
    public AiCodeGenTypeRoutingService aiCodeGenTypeRoutingService() {
        return AiServices.builder(AiCodeGenTypeRoutingService.class)
                .chatModel(chatModel)
                .build();
    }
}
