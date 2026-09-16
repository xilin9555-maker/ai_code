package com.tmz.aicode.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/**
 * 验证三类模型使用多例作用域。
 *
 * 测试只创建客户端对象并比较引用，不会发送网络请求或调用真实模型。
 */
class AiModelPrototypeConfigTest {

    @Test
    void everyModelLookupCreatesANewInstance() {
        try (AnnotationConfigApplicationContext context = createContext()) {
            assertNotSame(
                    context.getBean("streamingChatModelPrototype", StreamingChatModel.class),
                    context.getBean("streamingChatModelPrototype", StreamingChatModel.class)
            );
            assertNotSame(
                    context.getBean(
                            "reasoningStreamingChatModelPrototype",
                            StreamingChatModel.class
                    ),
                    context.getBean(
                            "reasoningStreamingChatModelPrototype",
                            StreamingChatModel.class
                    )
            );
            assertNotSame(
                    context.getBean("routingChatModelPrototype", ChatModel.class),
                    context.getBean("routingChatModelPrototype", ChatModel.class)
            );
        }
    }

    /**
     * 并发获取模型时，每个虚拟线程也必须拿到独立实例。
     */
    @Test
    void concurrentLookupsReceiveIndependentStreamingModels() throws InterruptedException {
        try (AnnotationConfigApplicationContext context = createContext()) {
            int taskCount = 6;
            Set<StreamingChatModel> models = ConcurrentHashMap.newKeySet();
            Thread[] threads = new Thread[taskCount];
            for (int index = 0; index < taskCount; index++) {
                threads[index] = Thread.ofVirtual().start(() -> models.add(
                        context.getBean(
                                "streamingChatModelPrototype",
                                StreamingChatModel.class
                        )
                ));
            }
            for (Thread thread : threads) {
                thread.join();
            }

            assertEquals(taskCount, models.size());
        }
    }

    /** 创建只包含三类模型配置的轻量 Spring 上下文。 */
    private AnnotationConfigApplicationContext createContext() {
        AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext();
        TestPropertyValues.of(
                "langchain4j.open-ai.streaming-chat-model.base-url=http://localhost:12345/v1",
                "langchain4j.open-ai.streaming-chat-model.api-key=test-key",
                "langchain4j.open-ai.streaming-chat-model.model-name=test-model",
                "langchain4j.open-ai.streaming-chat-model.max-tokens=1024",
                "langchain4j.open-ai.reasoning-streaming-chat-model.base-url=http://localhost:12345/v1",
                "langchain4j.open-ai.reasoning-streaming-chat-model.api-key=test-key",
                "langchain4j.open-ai.reasoning-streaming-chat-model.model-name=test-model",
                "langchain4j.open-ai.reasoning-streaming-chat-model.max-tokens=1024",
                "langchain4j.open-ai.routing-chat-model.base-url=http://localhost:12345/v1",
                "langchain4j.open-ai.routing-chat-model.api-key=test-key",
                "langchain4j.open-ai.routing-chat-model.model-name=test-model",
                "langchain4j.open-ai.routing-chat-model.max-tokens=1024"
        ).applyTo(context);
        context.register(
                StreamingChatModelConfig.class,
                ReasoningStreamingChatModelConfig.class,
                RoutingAiModelConfig.class
        );
        context.refresh();
        return context;
    }
}
