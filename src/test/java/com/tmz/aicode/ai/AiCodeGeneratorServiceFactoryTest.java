package com.tmz.aicode.ai;

import com.tmz.aicode.ai.tools.BaseTool;
import com.tmz.aicode.ai.tools.FileDeleteTool;
import com.tmz.aicode.ai.tools.FileDirReadTool;
import com.tmz.aicode.ai.tools.FileModifyTool;
import com.tmz.aicode.ai.tools.FileReadTool;
import com.tmz.aicode.ai.tools.FileWriteTool;
import com.tmz.aicode.ai.tools.ToolManager;
import com.tmz.aicode.model.enums.CodeGenTypeEnum;
import com.tmz.aicode.service.ChatHistoryService;
import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 AI Service 工厂的本地缓存和应用隔离规则。
 *
 * 模型与 Redis 存储都使用 Mockito 对象。测试只创建 LangChain4j 代理，不会执行生成方法，
 * 因此不会连接 Redis、访问网络或消耗模型额度。
 */
class AiCodeGeneratorServiceFactoryTest {

    private final ChatHistoryService chatHistoryService = mock(ChatHistoryService.class);

    private final ObjectProvider<StreamingChatModel> streamingModelProvider =
            mock();

    private final ObjectProvider<StreamingChatModel> reasoningModelProvider =
            mock();

    private AiCodeGeneratorServiceFactory serviceFactory;

    @BeforeEach
    void setUp() {
        when(streamingModelProvider.getObject()).thenAnswer(
                ignored -> mock(StreamingChatModel.class));
        when(reasoningModelProvider.getObject()).thenAnswer(
                ignored -> mock(StreamingChatModel.class));
        serviceFactory = new AiCodeGeneratorServiceFactory(
                mock(ChatModel.class),
                streamingModelProvider,
                reasoningModelProvider,
                mock(RedisChatMemoryStore.class),
                chatHistoryService,
                createToolManager()
        );
    }

    /**
     * 同一个应用连续获取服务时应命中 Caffeine，避免重复构造代理和对话记忆对象。
     */
    @Test
    void sameAppIdReusesCachedService() {
        AiCodeGeneratorService firstService = serviceFactory.getAiCodeGeneratorService(1001L);
        AiCodeGeneratorService secondService = serviceFactory.getAiCodeGeneratorService(1001L);

        assertSame(firstService, secondService, "相同 appId 应返回同一个缓存实例");
        verify(streamingModelProvider, times(1)).getObject();
        verify(chatHistoryService, times(1))
                .loadChatHistoryToMemory(eq(1001L), any(MessageWindowChatMemory.class), eq(20));
    }

    /**
     * 两种执行模式只决定是否经过工作流，不参与服务缓存键计算。
     * 因此它们用同一个应用和生成类型取服务时，会自然共享同一份模型会话记忆。
     */
    @Test
    void sameAppAndCodeTypeReuseMemoryAcrossExecutionModes() {
        AiCodeGeneratorService normalModeService = serviceFactory.getAiCodeGeneratorService(
                1002L, CodeGenTypeEnum.VUE_PROJECT);
        AiCodeGeneratorService workflowModeService = serviceFactory.getAiCodeGeneratorService(
                1002L, CodeGenTypeEnum.VUE_PROJECT);

        assertSame(normalModeService, workflowModeService,
                "相同应用和生成类型应共享同一个服务实例");
        verify(chatHistoryService, times(1)).loadChatHistoryToMemory(
                eq(1002L), any(MessageWindowChatMemory.class), eq(100));
    }

    /**
     * 不同应用必须使用不同服务实例，确保各自绑定的 Redis 记忆 id 不会混用。
     */
    @Test
    void differentAppIdsUseDifferentServices() {
        AiCodeGeneratorService firstService = serviceFactory.getAiCodeGeneratorService(1001L);
        AiCodeGeneratorService secondService = serviceFactory.getAiCodeGeneratorService(2001L);

        assertNotSame(firstService, secondService, "不同 appId 应创建彼此独立的服务实例");
        verify(streamingModelProvider, times(2)).getObject();
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
        verify(streamingModelProvider, times(1)).getObject();
        verify(reasoningModelProvider, times(1)).getObject();
        verify(chatHistoryService).loadChatHistoryToMemory(
                eq(1001L), any(MessageWindowChatMemory.class), eq(100));
    }

    /** 创建与生产环境一致的五个工程工具，验证工厂能够完成实际工具绑定。 */
    private static ToolManager createToolManager() {
        return new ToolManager(new BaseTool[]{
                new FileWriteTool(),
                new FileReadTool(),
                new FileModifyTool(),
                new FileDirReadTool(),
                new FileDeleteTool()
        });
    }
}
