package com.tmz.aicode.ai;

import com.tmz.aicode.ai.guardrail.PromptSafetyInputGuardrail;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 创建代码生成类型路由服务。
 *
 * 每次路由都使用一个新的非流式模型，避免多个创建请求共享模型实例后串行等待。
 */
@Configuration
@ConditionalOnProperty(prefix = "langchain4j.open-ai.chat-model", name = "api-key")
public class AiCodeGenTypeRoutingServiceFactory {

    private final ObjectProvider<ChatModel> routingChatModelProvider;

    /**
     * @param routingChatModelProvider 按需提供路由模型的新实例
     */
    public AiCodeGenTypeRoutingServiceFactory(
            @Qualifier("routingChatModelPrototype")
            ObjectProvider<ChatModel> routingChatModelProvider) {
        this.routingChatModelProvider = routingChatModelProvider;
    }

    /**
     * 创建由 LangChain4j 实现的路由代理，每次调用都会取得新的 prototype 模型。
     *
     * @return 可以把用户需求直接分类为 CodeGenTypeEnum 的服务
     */
    public AiCodeGenTypeRoutingService createAiCodeGenTypeRoutingService() {
        ChatModel chatModel = routingChatModelProvider.getObject();
        return AiServices.builder(AiCodeGenTypeRoutingService.class)
                .chatModel(chatModel)
                /*
                 * 创建应用时，初始化需求会先进入类型路由模型。这里提前注册护轨，确保
                 * 非法输入不会因为“尚未开始生成代码”而绕过安全检查。
                 */
                .inputGuardrails(new PromptSafetyInputGuardrail())
                .build();
    }

    /**
     * 保留默认 Bean，兼容只需要单个路由服务的独立调用。
     * 正式创建应用和工作流路由都会调用工厂方法获取新实例。
     *
     * @return 默认路由服务
     */
    @Bean
    public AiCodeGenTypeRoutingService aiCodeGenTypeRoutingService() {
        return createAiCodeGenTypeRoutingService();
    }
}
