package com.tmz.aicode.ai;

import com.tmz.aicode.service.ChatHistoryService;
import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import org.junit.jupiter.api.Test;
import com.tmz.aicode.model.enums.CodeGenTypeEnum;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 验证 AI Service 工厂的本地缓存和应用隔离规则。
 *
 * 模型与 Redis 存储都使用 Mockito 对象。测试只创建 LangChain4j 代理，不会执行生成方法，
 * 因此不会连接 Redis、访问网络或消耗模型额度。
 */
class AiCodeGeneratorServiceFactoryTest {

    private final ChatHistoryService chatHistoryService = mock(ChatHistoryService.class);

    private final AiCodeGeneratorServiceFactory serviceFactory =
            new AiCodeGeneratorServiceFactory(
                    mock(ChatModel.class),
                    mock(StreamingChatModel.class),
                    mock(StreamingChatModel.class),
                    mock(RedisChatMemoryStore.class),
                    chatHistoryService
            );

    /**
     * 同一个应用连续获取服务时应命中 Caffeine，避免重复构造代理和对话记忆对象。
     */
    @Test
    void sameAppIdReusesCachedService() {
        AiCodeGeneratorService firstService = serviceFactory.getAiCodeGeneratorService(1001L);
        AiCodeGeneratorService secondService = serviceFactory.getAiCodeGeneratorService(1001L);

        assertSame(firstService, secondService, "相同 appId 应返回同一个缓存实例");
        verify(chatHistoryService, times(1))
                .loadChatHistoryToMemory(eq(1001L), any(MessageWindowChatMemory.class), eq(20));
    }

    /**
     * 不同应用必须使用不同服务实例，确保各自绑定的 Redis 记忆 id 不会混用。
     */
    @Test
    void differentAppIdsUseDifferentServices() {
        AiCodeGeneratorService firstService = serviceFactory.getAiCodeGeneratorService(1001L);
        AiCodeGeneratorService secondService = serviceFactory.getAiCodeGeneratorService(2001L);

        assertNotSame(firstService, secondService, "不同 appId 应创建彼此独立的服务实例");
    }

    /**
     * 相同应用的原生模式和 Vue 模式需要使用不同代理，防止模型、记忆配置和工具混用。
     */
    @Test
    void differentCodeTypesUseDifferentServices() {
        AiCodeGeneratorService htmlService = serviceFactory.getAiCodeGeneratorService(
                1001L, CodeGenTypeEnum.HTML);
        AiCodeGeneratorService vueService = serviceFactory.getAiCodeGeneratorService(
                1001L, CodeGenTypeEnum.VUE_PROJECT);

        assertNotSame(htmlService, vueService, "不同生成类型应使用不同的缓存实例");
        verify(chatHistoryService).loadChatHistoryToMemory(
                eq(1001L), any(MessageWindowChatMemory.class), eq(100));
    }
}
