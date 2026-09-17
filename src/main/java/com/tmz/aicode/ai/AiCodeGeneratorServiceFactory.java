package com.tmz.aicode.ai;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.tmz.aicode.ai.guardrail.PromptSafetyInputGuardrail;
import com.tmz.aicode.ai.tools.ToolManager;
import com.tmz.aicode.exception.BusinessException;
import com.tmz.aicode.exception.ErrorCode;
import com.tmz.aicode.model.enums.CodeGenTypeEnum;
import com.tmz.aicode.service.ChatHistoryService;
import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * 创建网页代码生成服务的 Spring 配置。
 *
 * 工厂为每个应用取得独立的流式模型，并绑定以应用 id 隔离的 Redis 对话记忆，再生成
 * AiCodeGeneratorService 代理对象。
 * 创建完成的代理会按应用 id 缓存在本地，减少重复构造；缓存失效后重新创建的代理仍能
 * 从相同的 Redis Key 恢复上下文，因此本地缓存过期不会导致对话记忆丢失。
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "langchain4j.open-ai.chat-model", name = "api-key")
public class AiCodeGeneratorServiceFactory {

    private final ChatModel chatModel;
    private final ObjectProvider<StreamingChatModel> streamingChatModelProvider;
    private final ObjectProvider<StreamingChatModel> reasoningStreamingChatModelProvider;
    private final RedisChatMemoryStore redisChatMemoryStore;
    private final ChatHistoryService chatHistoryService;
    private final ToolManager toolManager;

    /**
     * 按应用缓存已经创建好的 AI Service。
     *
     * maximumSize 限制最多保留 1000 个应用实例；expireAfterWrite 保证实例最长只保留
     * 30 分钟；expireAfterAccess 会清理连续 10 分钟没有使用的实例。两个过期条件共同
     * 控制内存占用，任意一个先满足时，实例都会在缓存维护阶段被移除。
     */
    private final Cache<String, AiCodeGeneratorService> serviceCache =
            Caffeine.<String, AiCodeGeneratorService>newBuilder()
                    .maximumSize(1000)
                    .expireAfterWrite(Duration.ofMinutes(30))
                    .expireAfterAccess(Duration.ofMinutes(10))
                    .removalListener((cacheKey, service, cause) ->
                            log.debug("AI 服务实例已从缓存移除，缓存键：{}，原因：{}", cacheKey, cause))
                    .build();

    /**
     * 使用构造器接收统一的对话模型，依赖关系清晰，也便于在测试中替换为模拟模型。
     *
     * @param chatModel 已完成地址、密钥和模型名称配置的普通对话模型
     * @param streamingChatModelProvider 按需提供默认流式模型的新实例
     * @param reasoningStreamingChatModelProvider 按需提供推理流式模型的新实例
     * @param redisChatMemoryStore 负责持久化各个应用对话记忆的 Redis 存储
     * @param chatHistoryService 负责在缓存未命中时从数据库恢复已有对话
     * @param toolManager 统一提供 Vue 工程模式可调用的文件工具
     */
    public AiCodeGeneratorServiceFactory(@Qualifier("openAiChatModel") ChatModel chatModel,
                                         @Qualifier("streamingChatModelPrototype")
                                         ObjectProvider<StreamingChatModel>
                                                 streamingChatModelProvider,
                                         @Qualifier("reasoningStreamingChatModelPrototype")
                                         ObjectProvider<StreamingChatModel>
                                                 reasoningStreamingChatModelProvider,
                                         RedisChatMemoryStore redisChatMemoryStore,
                                         ChatHistoryService chatHistoryService,
                                         ToolManager toolManager) {
        this.chatModel = chatModel;
        this.streamingChatModelProvider = streamingChatModelProvider;
        this.reasoningStreamingChatModelProvider = reasoningStreamingChatModelProvider;
        this.redisChatMemoryStore = redisChatMemoryStore;
        this.chatHistoryService = chatHistoryService;
        this.toolManager = toolManager;
    }

    /**
     * 根据应用 id 获取带有独立对话记忆的 AI 服务。
     *
     * Caffeine 的 get 操作会先查本地缓存。命中时直接返回现有实例；未命中时才调用创建
     * 方法，而且同一个 key 并发加载时只会保留一个结果，避免重复构造相同应用的服务。
     *
     * @param appId 应用唯一标识，也是 Redis 对话记忆的隔离标识
     * @return 当前应用对应的、可以复用的网页代码生成服务
     */
    public AiCodeGeneratorService getAiCodeGeneratorService(long appId) {
        return getAiCodeGeneratorService(appId, CodeGenTypeEnum.HTML);
    }

    /**
     * 根据应用和生成类型获取对应的 AI Service。
     *
     * 同一个应用在不同生成模式下会使用不同模型和工具，因此缓存键必须同时包含 appId
     * 和 codeGenType。这样既能复用已经创建的代理，又不会把 Vue 的推理模型误用于原生
     * HTML 生成。
     *
     * @param appId 应用唯一标识
     * @param codeGenType 当前应用采用的代码生成类型
     * @return 与应用和生成类型同时匹配的 AI Service
     */
    public AiCodeGeneratorService getAiCodeGeneratorService(long appId,
                                                             CodeGenTypeEnum codeGenType) {
        if (codeGenType == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "代码生成类型不能为空");
        }
        String cacheKey = buildCacheKey(appId, codeGenType);
        return serviceCache.get(cacheKey,
                ignored -> createAiCodeGeneratorService(appId, codeGenType));
    }

    /**
     * 在缓存未命中时创建与生成类型匹配的 AI Service。
     *
     * appId 同时作为 MessageWindowChatMemory 的 id。LangChain4j 会用这个 id 访问 Redis，
     * 因而同一个应用能够读取自己的历史上下文，不同应用即使由同一用户创建也不会串话。
     * Vue 工程的一轮生成会产生多次工具请求和执行结果，因此为它保留更大的消息窗口，
     * 防止生成到一半时遗忘最初需求和已经创建的文件。
     *
     * @param appId 应用唯一标识
     * @param codeGenType 当前代码生成类型
     * @return 新创建并绑定当前应用记忆的 AI Service
     */
    private AiCodeGeneratorService createAiCodeGeneratorService(long appId,
                                                                CodeGenTypeEnum codeGenType) {
        log.info("正在为应用创建 AI 服务实例，应用 id：{}，生成类型：{}",
                appId, codeGenType.getValue());
        int maxMessages = codeGenType == CodeGenTypeEnum.VUE_PROJECT ? 100 : 20;
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                .id(appId)
                .chatMemoryStore(redisChatMemoryStore)
                .maxMessages(maxMessages)
                .build();

        // 只有 Caffeine 未命中并创建服务时才恢复历史，避免每轮对话都重复查询数据库。
        chatHistoryService.loadChatHistoryToMemory(appId, chatMemory, maxMessages);

        return switch (codeGenType) {
            case VUE_PROJECT -> {
                // 每个工程服务绑定新的推理流式模型，使不同应用可以并行消费模型响应。
                StreamingChatModel reasoningStreamingChatModel =
                        reasoningStreamingChatModelProvider.getObject();
                yield AiServices.builder(AiCodeGeneratorService.class)
                        .streamingChatModel(reasoningStreamingChatModel)
                        // 在消息进入模型及工具调用链之前拒绝异常输入。
                        .inputGuardrails(new PromptSafetyInputGuardrail())
                        // 服务方法声明了 @MemoryId，因此这里必须提供按 memoryId 获取记忆的方式。
                        .chatMemoryProvider(memoryId -> chatMemory)
                        // 显式转成 Object[]，确保数组按可变参数展开，而不是被当作一个工具对象。
                        .tools((Object[]) toolManager.getAllTools())
                        // 模型偶尔会编造工具名。把错误作为工具结果返回，可让模型自行改正并继续。
                        .hallucinatedToolNameStrategy(toolRequest -> ToolExecutionResultMessage.from(
                                toolRequest,
                                "工具不存在：" + toolRequest.name() + "，请只使用已提供的工具"
                        ))
                        .maxSequentialToolsInvocations(40)
                        .build();
            }
            case HTML, MULTI_FILE -> {
                // 普通网页服务也按应用取得独立流式模型，避免共享模型导致请求排队。
                StreamingChatModel streamingChatModel = streamingChatModelProvider.getObject();
                yield AiServices.builder(AiCodeGeneratorService.class)
                        .chatModel(chatModel)
                        .streamingChatModel(streamingChatModel)
                        // HTML 和多文件模式同样执行输入审查，不能只保护工程模式。
                        .inputGuardrails(new PromptSafetyInputGuardrail())
                        .chatMemory(chatMemory)
                        .build();
            }
        };
    }

    /**
     * 构造同时包含应用 id 和生成类型的稳定缓存键。
     */
    private String buildCacheKey(long appId, CodeGenTypeEnum codeGenType) {
        return appId + "_" + codeGenType.getValue();
    }

    /**
     * 保留一个默认 AI 服务 Bean，兼容直接注入 AiCodeGeneratorService 的独立测试和工具代码。
     * 正式的应用生成流程会通过 getAiCodeGeneratorService(appId) 获取带应用隔离的实例。
     *
     * @return 使用默认记忆 id 的 AI 服务
     */
    @Bean
    public AiCodeGeneratorService aiCodeGeneratorService() {
        return getAiCodeGeneratorService(0L);
    }
}
