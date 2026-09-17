package com.tmz.aicode.ai.guardrail;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import dev.langchain4j.guardrail.config.OutputGuardrailsConfig;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.service.AiServices;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 输出重试护轨的纯本地单元测试。
 *
 * 测试直接构造模型消息，不启动 Spring 容器，也不会调用真实模型或其他外部服务。
 */
class RetryOutputGuardrailTest {

    private final RetryOutputGuardrail guardrail = new RetryOutputGuardrail();

    /** 完整且不包含敏感凭据的网页代码应该正常通过。 */
    @Test
    void acceptsCompleteResponse() {
        OutputGuardrailResult result = validate(
                "<!DOCTYPE html><html><body><h1>个人作品集</h1></body></html>"
        );

        assertTrue(result.isSuccess());
        assertFalse(result.isRetry());
    }

    /** 空响应应附加完整生成要求后重新请求模型。 */
    @Test
    void repromptsEmptyResponse() {
        AiMessage emptyMessage = mock(AiMessage.class);
        when(emptyMessage.text()).thenReturn("   ");

        OutputGuardrailResult result = guardrail.validate(emptyMessage);

        assertReprompt(result, "响应内容为空", "请重新生成完整的内容");
    }

    /** 只有几个字的响应不足以形成代码，应要求返回更完整的内容。 */
    @Test
    void repromptsShortResponse() {
        OutputGuardrailResult result = validate("已完成");

        assertReprompt(
                result,
                "响应内容过短",
                "请提供更详细、完整且可以直接使用的内容"
        );
    }

    /** 硬编码的长密码属于疑似凭据，不应进入文件保存流程。 */
    @Test
    void repromptsHardCodedCredential() {
        OutputGuardrailResult result = validate(
                "const password = \"production-password-123\";"
        );

        assertReprompt(
                result,
                "响应包含疑似敏感凭据",
                "请重新生成内容，移除密码、密钥、令牌、私钥等敏感凭据"
        );
    }

    /** 登录表单的字段类型只是正常页面代码，不能因为出现 password 单词就误判。 */
    @Test
    void allowsPasswordFormFieldWithoutCredentialValue() {
        OutputGuardrailResult result = validate(
                "<label>密码</label><input type=\"password\" autocomplete=\"current-password\">"
        );

        assertTrue(result.isSuccess());
    }

    /** 私钥正文的固定头部具有明确风险，应要求模型移除后重新生成。 */
    @Test
    void repromptsPrivateKeyContent() {
        OutputGuardrailResult result = validate(
                "-----BEGIN PRIVATE KEY-----\nMIIEvQIBADANBgkqhkiG9w0BAQEFAASC"
        );

        assertTrue(result.isReprompt());
        assertEquals("响应包含疑似敏感凭据", result.failures().getFirst().message());
    }

    /**
     * 使用可控的本地模型验证完整重试链路。第一次返回过短内容后，框架应携带补充提示再次
     * 请求；第二次返回合格内容后结束，调用方只能得到最终通过检查的响应。
     */
    @Test
    void aiServiceRepromptsAndReturnsValidatedResponse() {
        SequenceChatModel chatModel = new SequenceChatModel(List.of(
                "太短",
                "这是重新生成后的完整网页内容，已经满足输出质量要求。"
        ));
        GuardedAssistant assistant = AiServices.builder(GuardedAssistant.class)
                .chatModel(chatModel)
                .outputGuardrails(guardrail)
                .outputGuardrailsConfig(OutputGuardrailsConfig.builder().maxRetries(3).build())
                .build();

        String response = assistant.chat("生成一个个人主页");

        assertEquals(2, chatModel.callCount());
        assertEquals("这是重新生成后的完整网页内容，已经满足输出质量要求。", response);
        assertTrue(
                chatModel.requests().get(1).messages().stream()
                        .anyMatch(message -> message.toString().contains("请提供更详细、完整且可以直接使用的内容"))
        );
    }

    /**
     * 流式调用也必须丢弃首次未通过的片段，只向下游发送重试后的合格内容。内存模型会
     * 同步触发回调，因此测试无需网络、等待或真实模型配额。
     */
    @Test
    void streamingAiServiceEmitsOnlyValidatedRetryResponse() {
        SequenceStreamingChatModel streamingChatModel = new SequenceStreamingChatModel(List.of(
                "太短",
                "这是流式重试后生成的完整网页内容，能够安全交给下游保存。"
        ));
        StreamingGuardedAssistant assistant = AiServices.builder(StreamingGuardedAssistant.class)
                .streamingChatModel(streamingChatModel)
                .outputGuardrails(guardrail)
                .outputGuardrailsConfig(OutputGuardrailsConfig.builder().maxRetries(3).build())
                .build();

        List<String> chunks = assistant.chat("生成一个作品展示页")
                .collectList()
                .block();

        assertEquals(2, streamingChatModel.callCount());
        assertEquals(List.of("这是流式重试后生成的完整网页内容，能够安全交给下游保存。"), chunks);
        assertTrue(
                streamingChatModel.requests().get(1).messages().stream()
                        .anyMatch(message -> message.toString().contains("请提供更详细、完整且可以直接使用的内容"))
        );
    }

    /** 把普通文本包装成框架实际交给护轨的 AI 消息。 */
    private OutputGuardrailResult validate(String response) {
        return guardrail.validate(AiMessage.from(response));
    }

    /** 同时验证重试标识、失败原因和下一次请求使用的补充提示。 */
    private void assertReprompt(OutputGuardrailResult result,
                                String expectedReason,
                                String expectedReprompt) {
        assertTrue(result.isRetry());
        assertTrue(result.isReprompt());
        assertFalse(result.failures().isEmpty());
        assertEquals(expectedReason, result.failures().getFirst().message());
        assertEquals(expectedReprompt, result.getReprompt().orElseThrow());
    }

    /** 只用于验证 AiServices 输出护轨重试行为的最小服务接口。 */
    private interface GuardedAssistant {

        String chat(String userMessage);
    }

    /** 只用于验证流式输出护轨与 Reactor 适配结果的最小服务接口。 */
    private interface StreamingGuardedAssistant {

        Flux<String> chat(String userMessage);
    }

    /**
     * 按预设顺序返回响应的内存模型。
     *
     * 每次调用都会保存请求，便于断言第二次请求确实包含护轨产生的补充提示。响应全部用完
     * 后继续返回最后一项，使测试即使发生额外重试也能得到确定结果。
     */
    private static class SequenceChatModel implements ChatModel {

        private final List<String> responses;
        private final List<ChatRequest> requests = new ArrayList<>();
        private final AtomicInteger calls = new AtomicInteger();

        private SequenceChatModel(List<String> responses) {
            this.responses = List.copyOf(responses);
        }

        @Override
        public ChatResponse doChat(ChatRequest request) {
            requests.add(request);
            int responseIndex = Math.min(calls.getAndIncrement(), responses.size() - 1);
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from(responses.get(responseIndex)))
                    .build();
        }

        private int callCount() {
            return calls.get();
        }

        private List<ChatRequest> requests() {
            return List.copyOf(requests);
        }
    }

    /**
     * 流式版本的可控内存模型。每轮将预设文本作为一个片段发送，并使用同一文本构造完整
     * 响应，便于准确判断首次片段有没有在护轨通过前泄漏给调用方。
     */
    private static class SequenceStreamingChatModel implements StreamingChatModel {

        private final List<String> responses;
        private final List<ChatRequest> requests = new ArrayList<>();
        private final AtomicInteger calls = new AtomicInteger();

        private SequenceStreamingChatModel(List<String> responses) {
            this.responses = List.copyOf(responses);
        }

        @Override
        public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
            requests.add(request);
            int responseIndex = Math.min(calls.getAndIncrement(), responses.size() - 1);
            String response = responses.get(responseIndex);
            handler.onPartialResponse(response);
            handler.onCompleteResponse(ChatResponse.builder()
                    .aiMessage(AiMessage.from(response))
                    .metadata(ChatResponseMetadata.builder()
                            .tokenUsage(new TokenUsage())
                            .build())
                    .build());
        }

        private int callCount() {
            return calls.get();
        }

        private List<ChatRequest> requests() {
            return List.copyOf(requests);
        }
    }
}
