package com.tmz.aicode.core.handler;

import cn.hutool.json.JSONUtil;
import com.tmz.aicode.ai.model.message.AiResponseMessage;
import com.tmz.aicode.ai.model.message.StreamMessageTypeEnum;
import com.tmz.aicode.ai.model.message.ToolExecutedMessage;
import com.tmz.aicode.ai.model.message.ToolRequestMessage;
import com.tmz.aicode.constant.AppConstant;
import com.tmz.aicode.core.builder.VueProjectBuilder;
import com.tmz.aicode.model.entity.User;
import com.tmz.aicode.service.ChatHistoryService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 使用本地 JSON 消息验证 Vue 工程流的转换过程，不调用模型或执行文件写入工具。
 */
class JsonMessageStreamHandlerTest {

    /**
     * 工具参数里的文件内容应在执行完成前逐段输出，完成事件只负责补齐内容并关闭代码块。
     */
    @Test
    void handlesToolChunksAndBuildsStableChatHistory() {
        long appId = 3001L;
        long userId = 1001L;
        User loginUser = User.builder().id(userId).build();
        ChatHistoryService chatHistoryService = mock(ChatHistoryService.class);
        VueProjectBuilder vueProjectBuilder = mock(VueProjectBuilder.class);
        when(chatHistoryService.addChatMessage(
                eq(appId),
                org.mockito.ArgumentMatchers.anyString(),
                eq("ai"),
                eq(userId)
        )).thenReturn(true);

        ToolRequestMessage firstRequestChunk = createToolRequest(
                "call_1",
                "{\"relativeFilePath\":\"src/App.vue\",\"content\":\"<template>\\n"
        );
        ToolRequestMessage secondRequestChunk = createToolRequest(
                "call_1",
                "  <h1>任务"
        );
        ToolRequestMessage lastRequestChunk = createToolRequest(
                "call_1",
                "管理</h1>\\n</template>\"}"
        );
        ToolExecutedMessage executedMessage = new ToolExecutedMessage();
        executedMessage.setType(StreamMessageTypeEnum.TOOL_EXECUTED.getValue());
        executedMessage.setId("call_1");
        executedMessage.setName("writeFile");
        executedMessage.setArguments(JSONUtil.toJsonStr(Map.of(
                "relativeFilePath", "src/App.vue",
                "content", "<template><h1>任务管理</h1></template>"
        )));
        executedMessage.setResult("文件写入成功：src/App.vue");

        Flux<String> originFlux = Flux.just(
                JSONUtil.toJsonStr(new AiResponseMessage("开始生成工程。")),
                JSONUtil.toJsonStr(firstRequestChunk),
                JSONUtil.toJsonStr(secondRequestChunk),
                JSONUtil.toJsonStr(lastRequestChunk),
                JSONUtil.toJsonStr(executedMessage),
                JSONUtil.toJsonStr(new AiResponseMessage("工程生成完成。"))
        );

        List<String> output = new JsonMessageStreamHandler(vueProjectBuilder)
                .handle(originFlux, chatHistoryService, appId, loginUser)
                .collectList()
                .block();

        assertNotNull(output);
        assertEquals(6, output.size(), "三个代码片段应分别进入下游流");
        assertEquals(1, output.stream()
                .filter(chunk -> chunk.contains("[选择工具]"))
                .count());
        assertTrue(output.get(1).contains("<template>\n"));
        assertEquals("  <h1>任务", output.get(2));
        assertEquals("管理</h1>\n</template>", output.get(3));
        assertEquals("\n```\n\n", output.get(4), "工具完成时不应重复发送整份文件");
        String response = String.join("", output);
        assertTrue(response.contains("[工具调用] 写入文件 src/App.vue"));
        assertTrue(response.contains("```vue"));
        assertTrue(response.contains("<template>\n  <h1>任务管理</h1>\n</template>"));

        ArgumentCaptor<String> historyCaptor = ArgumentCaptor.forClass(String.class);
        verify(chatHistoryService).addChatMessage(
                eq(appId),
                historyCaptor.capture(),
                eq("ai"),
                eq(userId)
        );
        String savedHistory = historyCaptor.getValue();
        assertFalse(savedHistory.contains("[选择工具]"));
        assertTrue(savedHistory.startsWith("开始生成工程。"));
        assertTrue(savedHistory.contains("[工具调用] 写入文件 src/App.vue"));
        assertTrue(savedHistory.endsWith("工程生成完成。"));
        verify(vueProjectBuilder).buildProjectAsync(new File(
                AppConstant.CODE_OUTPUT_ROOT_DIR,
                "vue_project_" + appId
        ).getAbsolutePath());
    }

    /**
     * 创建一个工具请求片段，方便模拟同一调用 id 的增量参数。
     */
    private ToolRequestMessage createToolRequest(String id, String arguments) {
        ToolRequestMessage message = new ToolRequestMessage();
        message.setType(StreamMessageTypeEnum.TOOL_REQUEST.getValue());
        message.setId(id);
        message.setName("writeFile");
        message.setArguments(arguments);
        return message;
    }
}
