package com.tmz.aicode.core.handler;

import cn.hutool.json.JSONUtil;
import com.tmz.aicode.ai.model.message.AiResponseMessage;
import com.tmz.aicode.ai.model.message.BuildProgressMessage;
import com.tmz.aicode.ai.model.message.StreamMessageTypeEnum;
import com.tmz.aicode.ai.model.message.ToolExecutedMessage;
import com.tmz.aicode.ai.model.message.ToolRequestMessage;
import com.tmz.aicode.ai.tools.BaseTool;
import com.tmz.aicode.ai.tools.ExitTool;
import com.tmz.aicode.ai.tools.FileDeleteTool;
import com.tmz.aicode.ai.tools.FileDirReadTool;
import com.tmz.aicode.ai.tools.FileModifyTool;
import com.tmz.aicode.ai.tools.FileReadTool;
import com.tmz.aicode.ai.tools.FileWriteTool;
import com.tmz.aicode.ai.tools.ToolManager;
import com.tmz.aicode.model.entity.User;
import com.tmz.aicode.model.dto.build.BuildProgress;
import com.tmz.aicode.model.vo.GenerationStreamEvent;
import com.tmz.aicode.service.ChatHistoryService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 使用本地 JSON 消息验证 Vue 工程流的转换过程，不调用模型或执行文件写入工具。
 */
class JsonMessageStreamHandlerTest {

    /** 同一工具调用的参数分片只产生一次选择提示，完成后再展示准确的完整参数。 */
    @Test
    void handlesToolChunksAndBuildsStableChatHistory() {
        long appId = 3001L;
        long userId = 1001L;
        User loginUser = User.builder().id(userId).build();
        ChatHistoryService chatHistoryService = mock(ChatHistoryService.class);
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

        List<String> output = new JsonMessageStreamHandler(createToolManager())
                .handle(originFlux, chatHistoryService, appId, loginUser)
                .map(this::messageContent)
                .collectList()
                .block();

        assertNotNull(output);
        assertEquals(4, output.size(), "重复参数分片不应产生多条前端提示");
        assertEquals(1, output.stream()
                .filter(chunk -> chunk.contains("[选择工具]"))
                .count());
        assertTrue(output.get(1).contains("[选择工具] 写入文件"));
        assertTrue(output.get(2).contains("[工具调用] 写入文件 src/App.vue"));
        assertTrue(output.get(2).contains("<template><h1>任务管理</h1></template>"));
        String response = String.join("", output);
        assertTrue(response.contains("[工具调用] 写入文件 src/App.vue"));
        assertTrue(response.contains("```vue"));
        assertTrue(response.contains("<template><h1>任务管理</h1></template>"));

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
    }

    /** 并行工具按调用 id 分别提示，完整执行信息需要形成彼此独立的代码块。 */
    @Test
    void streamsInterleavedToolsAndSavesStableMarkdown() {
        long appId = 3002L;
        long userId = 1002L;
        User loginUser = User.builder().id(userId).build();
        ChatHistoryService chatHistoryService = mock(ChatHistoryService.class);
        when(chatHistoryService.addChatMessage(
                eq(appId),
                org.mockito.ArgumentMatchers.anyString(),
                eq("ai"),
                eq(userId)
        )).thenReturn(true);

        Flux<String> originFlux = Flux.just(
                JSONUtil.toJsonStr(createToolRequest(
                        "call_1",
                        "{\"relativeFilePath\":\"src/App.vue\",\"content\":\"<template>首页"
                )),
                JSONUtil.toJsonStr(createToolRequest(
                        "call_2",
                        "{\"relativeFilePath\":\"package.json\",\"content\":\"{\\\"name\\\":"
                )),
                JSONUtil.toJsonStr(createToolExecuted(
                        "call_1", "src/App.vue", "<template>首页</template>"
                )),
                JSONUtil.toJsonStr(createToolExecuted(
                        "call_2", "package.json", "{\"name\":\"demo\"}"
                )),
                JSONUtil.toJsonStr(new AiResponseMessage("工程生成完成。"))
        );

        List<String> output = new JsonMessageStreamHandler(createToolManager())
                .handle(originFlux, chatHistoryService, appId, loginUser)
                .map(this::messageContent)
                .collectList()
                .block();

        assertNotNull(output);
        assertEquals(5, output.size());
        assertTrue(output.get(0).contains("[选择工具] 写入文件"));
        assertTrue(output.get(1).contains("[选择工具] 写入文件"));
        assertTrue(output.get(2).contains("<template>首页</template>"));
        assertTrue(output.get(3).contains("{\"name\":\"demo\"}"));

        ArgumentCaptor<String> historyCaptor = ArgumentCaptor.forClass(String.class);
        verify(chatHistoryService).addChatMessage(
                eq(appId),
                historyCaptor.capture(),
                eq("ai"),
                eq(userId)
        );
        String savedHistory = historyCaptor.getValue();
        assertEquals(4, countOccurrences(savedHistory, "```"));
        assertTrue(savedHistory.contains("```vue\n<template>首页</template>\n```"));
        assertTrue(savedHistory.contains("```json\n{\"name\":\"demo\"}\n```"));
        assertTrue(savedHistory.endsWith("工程生成完成。"));
    }

    /** 修改工具应通过管理器展示目标路径以及替换前后的完整内容。 */
    @Test
    void formatsIncrementalModificationDetails() {
        long appId = 3003L;
        long userId = 1003L;
        User loginUser = User.builder().id(userId).build();
        ChatHistoryService chatHistoryService = mock(ChatHistoryService.class);
        when(chatHistoryService.addChatMessage(
                eq(appId),
                org.mockito.ArgumentMatchers.anyString(),
                eq("ai"),
                eq(userId)
        )).thenReturn(true);

        ToolRequestMessage requestMessage = createToolRequest(
                "call_modify",
                "modifyFile",
                "{\"relativeFilePath\":\"src/App.vue\""
        );
        ToolExecutedMessage executedMessage = new ToolExecutedMessage();
        executedMessage.setType(StreamMessageTypeEnum.TOOL_EXECUTED.getValue());
        executedMessage.setId("call_modify");
        executedMessage.setName("modifyFile");
        executedMessage.setArguments(JSONUtil.toJsonStr(Map.of(
                "relativeFilePath", "src/App.vue",
                "oldContent", "<h1>旧标题</h1>",
                "newContent", "<h1>新标题</h1>"
        )));
        executedMessage.setResult("文件修改成功：src/App.vue");

        List<String> output = new JsonMessageStreamHandler(createToolManager())
                .handle(Flux.just(
                        JSONUtil.toJsonStr(requestMessage),
                        JSONUtil.toJsonStr(executedMessage)
                ), chatHistoryService, appId, loginUser)
                .map(this::messageContent)
                .collectList()
                .block();

        assertNotNull(output);
        assertEquals(2, output.size());
        assertTrue(output.get(0).contains("[选择工具] 修改文件"));
        assertTrue(output.get(1).contains("[工具调用] 修改文件 src/App.vue"));
        assertTrue(output.get(1).contains("替换前：\n```\n<h1>旧标题</h1>\n```"));
        assertTrue(output.get(1).contains("替换后：\n```\n<h1>新标题</h1>\n```"));

        ArgumentCaptor<String> historyCaptor = ArgumentCaptor.forClass(String.class);
        verify(chatHistoryService).addChatMessage(
                eq(appId),
                historyCaptor.capture(),
                eq("ai"),
                eq(userId)
        );
        assertFalse(historyCaptor.getValue().contains("[选择工具]"));
        assertTrue(historyCaptor.getValue().contains("替换前："));
        assertTrue(historyCaptor.getValue().contains("替换后："));
    }

    /** 流处理器在完成时只保存回复，项目构建由上游生成流程负责。 */
    @Test
    void completedStreamOnlySavesChatHistory() {
        long appId = 3004L;
        long userId = 1004L;
        User loginUser = User.builder().id(userId).build();
        ChatHistoryService chatHistoryService = mock(ChatHistoryService.class);
        when(chatHistoryService.addChatMessage(
                eq(appId),
                org.mockito.ArgumentMatchers.anyString(),
                eq("ai"),
                eq(userId)
        )).thenReturn(true);

        List<String> output = new JsonMessageStreamHandler(createToolManager())
                .handle(
                        Flux.just(JSONUtil.toJsonStr(
                                new AiResponseMessage("工作流处理完成。"))),
                        chatHistoryService,
                        appId,
                        loginUser
                )
                .map(this::messageContent)
                .collectList()
                .block();

        assertEquals(List.of("工作流处理完成。"), output);
        verify(chatHistoryService).addChatMessage(
                appId, "工作流处理完成。", "ai", userId);
    }

    /** 构建进度应成为具名事件，并且不能写入 AI 对话历史。 */
    @Test
    void convertsBuildProgressToNamedEventWithoutSavingHistory() {
        long appId = 3005L;
        User loginUser = User.builder().id(1005L).build();
        ChatHistoryService chatHistoryService = mock(ChatHistoryService.class);
        BuildProgress progress = BuildProgress.running(
                "compile_assets", 60, "正在编译项目资源");

        List<GenerationStreamEvent> output = new JsonMessageStreamHandler(createToolManager())
                .handle(
                        Flux.just(JSONUtil.toJsonStr(new BuildProgressMessage(progress))),
                        chatHistoryService,
                        appId,
                        loginUser
                )
                .collectList()
                .block();

        assertNotNull(output);
        assertEquals(1, output.size());
        assertEquals(BuildProgress.EVENT_BUILD_PROGRESS, output.getFirst().getEvent());
        assertEquals(progress, output.getFirst().getData());
        verifyNoInteractions(chatHistoryService);
    }

    /**
     * 创建一个工具请求片段，方便模拟同一调用 id 的增量参数。
     */
    private ToolRequestMessage createToolRequest(String id, String arguments) {
        return createToolRequest(id, "writeFile", arguments);
    }

    /** 创建指定名称的工具请求片段，用于验证管理器的分发行为。 */
    private ToolRequestMessage createToolRequest(String id, String name, String arguments) {
        ToolRequestMessage message = new ToolRequestMessage();
        message.setType(StreamMessageTypeEnum.TOOL_REQUEST.getValue());
        message.setId(id);
        message.setName(name);
        message.setArguments(arguments);
        return message;
    }

    /** 创建包含完整文件参数的工具执行完成消息。 */
    private ToolExecutedMessage createToolExecuted(String id, String path, String content) {
        ToolExecutedMessage message = new ToolExecutedMessage();
        message.setType(StreamMessageTypeEnum.TOOL_EXECUTED.getValue());
        message.setId(id);
        message.setName("writeFile");
        message.setArguments(JSONUtil.toJsonStr(Map.of(
                "relativeFilePath", path,
                "content", content
        )));
        message.setResult("文件写入成功：" + path);
        return message;
    }

    /** 创建处理器测试使用的完整工具注册表。 */
    private ToolManager createToolManager() {
        return new ToolManager(new BaseTool[]{
                new FileWriteTool(),
                new FileReadTool(),
                new FileModifyTool(),
                new FileDirReadTool(),
                new FileDeleteTool(),
                new ExitTool()
        });
    }

    /** 统计 Markdown 围栏数量，用于确认所有代码块都已成对闭合。 */
    private int countOccurrences(String value, String target) {
        int count = 0;
        int index = 0;
        while ((index = value.indexOf(target, index)) >= 0) {
            count++;
            index += target.length();
        }
        return count;
    }

    /** 读取普通消息中的 d 字段，便于复用原有文本断言。 */
    private String messageContent(GenerationStreamEvent event) {
        assertEquals(GenerationStreamEvent.MESSAGE_EVENT, event.getEvent());
        return String.valueOf(((Map<?, ?>) event.getData()).get("d"));
    }
}
