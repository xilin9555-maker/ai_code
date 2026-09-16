package com.tmz.aicode.core.handler;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.tmz.aicode.ai.model.message.AiResponseMessage;
import com.tmz.aicode.ai.model.message.BuildProgressMessage;
import com.tmz.aicode.ai.model.message.StreamMessage;
import com.tmz.aicode.ai.model.message.StreamMessageTypeEnum;
import com.tmz.aicode.ai.model.message.ToolExecutedMessage;
import com.tmz.aicode.ai.model.message.ToolRequestMessage;
import com.tmz.aicode.ai.tools.BaseTool;
import com.tmz.aicode.ai.tools.ToolManager;
import com.tmz.aicode.exception.ErrorCode;
import com.tmz.aicode.exception.ThrowUtils;
import com.tmz.aicode.model.entity.User;
import com.tmz.aicode.model.enums.ChatHistoryMessageTypeEnum;
import com.tmz.aicode.model.vo.GenerationStreamEvent;
import com.tmz.aicode.service.ChatHistoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.HashSet;
import java.util.Set;

/**
 * 处理 Vue 工程模式产生的统一 JSON 消息流。
 *
 * 门面发送的消息中既有 AI 文本，也有工具请求和执行结果。该处理器把内部 JSON 事件
 * 转换成人可以阅读的流式文本，同时整理一份稳定的 Markdown 内容保存到对话历史。
 */
@Slf4j
@Component
public class JsonMessageStreamHandler {

    private final ToolManager toolManager;

    /**
     * 注入工具管理器，用于把工具调用转换成适合前端和历史记录展示的内容。
     *
     * @param toolManager 负责查找工具及其对应的展示策略
     */
    public JsonMessageStreamHandler(ToolManager toolManager) {
        this.toolManager = toolManager;
    }

    /**
     * 解析统一消息、生成前端文本，并在流结束后保存完整对话。
     *
     * 工具请求参数可能分成多个不完整片段到达，因此请求阶段只按调用 id 展示一次工具
     * 中文名称。执行完成事件携带完整参数，此时再交给具体工具生成准确的路径、代码或
     * 修改前后对比信息，并将同一份内容保存到对话历史。
     *
     * @param originFlux 门面输出的统一 JSON 消息流
     * @param chatHistoryService 对话历史服务
     * @param appId 当前应用 id
     * @param loginUser 发起生成的登录用户
     * @return 普通回复与构建进度组成的结构化响应流
     */
    public Flux<GenerationStreamEvent> handle(Flux<String> originFlux,
                                              ChatHistoryService chatHistoryService,
                                              long appId,
                                              User loginUser) {
        return Flux.defer(() -> {
            StringBuilder chatHistoryBuilder = new StringBuilder();
            Set<String> seenToolRequestIds = new HashSet<>();
            return originFlux
                    .<GenerationStreamEvent>handle((chunk, sink) -> {
                        GenerationStreamEvent event = handleJsonMessageChunk(
                                chunk,
                                chatHistoryBuilder,
                                seenToolRequestIds
                        );
                        // 重复工具参数片段没有展示增量，不能向 Reactor 的 map 返回 null。
                        if (event != null) {
                            sink.next(event);
                        }
                    })
                    .doOnComplete(() -> handleCompletedResponse(
                            chatHistoryService,
                            appId,
                            loginUser,
                            chatHistoryBuilder
                    ))
                    .doOnError(error -> saveFailureResponse(
                            chatHistoryService,
                            appId,
                            loginUser.getId(),
                            error
                    ));
        });
    }

    /**
     * 流正常结束后保存完整回复。
     *
     * Vue 工程构建已经移动到模型完成回调或工作流构建节点；能够进入这里说明构建也已
     * 成功，因此该处理器只负责保存对话，避免在完成信号之后重复启动后台构建。
     */
    private void handleCompletedResponse(ChatHistoryService chatHistoryService,
                                         long appId,
                                         User loginUser,
                                         StringBuilder chatHistoryBuilder) {
        saveCompletedResponse(
                chatHistoryService,
                appId,
                loginUser.getId(),
                chatHistoryBuilder.toString()
        );
    }

    /**
     * 根据 type 字段处理一个内部 JSON 消息。
     *
     * AI 文本会直接进入前端和历史记录；工具请求输出一次中文选择提示；工具完成消息
     * 根据具体工具生成参数详情，同时把相同内容写入历史记录。
     */
    private GenerationStreamEvent handleJsonMessageChunk(
            String chunk,
            StringBuilder chatHistoryBuilder,
            Set<String> seenToolRequestIds) {
        StreamMessage streamMessage = JSONUtil.toBean(chunk, StreamMessage.class);
        StreamMessageTypeEnum type = StreamMessageTypeEnum.getEnumByValue(streamMessage.getType());
        if (type == null) {
            log.warn("收到无法识别的流式消息类型：{}", streamMessage.getType());
            return null;
        }

        if (type == StreamMessageTypeEnum.BUILD_PROGRESS) {
            BuildProgressMessage message = JSONUtil.toBean(chunk, BuildProgressMessage.class);
            if (message.getData() == null) {
                log.warn("收到缺少 data 的构建进度消息");
                return null;
            }
            return GenerationStreamEvent.build(message.getData());
        }

        String content = switch (type) {
            case AI_RESPONSE -> handleAiResponse(chunk, chatHistoryBuilder);
            case TOOL_REQUEST -> handleToolRequest(chunk, seenToolRequestIds);
            case TOOL_EXECUTED -> handleToolExecuted(chunk, chatHistoryBuilder);
            case BUILD_PROGRESS -> "";
        };
        return StrUtil.isEmpty(content) ? null : GenerationStreamEvent.message(content);
    }

    /**
     * 处理普通 AI 文本，保持模型原有的输出顺序和空格。
     */
    private String handleAiResponse(String chunk, StringBuilder chatHistoryBuilder) {
        AiResponseMessage message = JSONUtil.toBean(chunk, AiResponseMessage.class);
        String data = message.getData();
        if (data == null) {
            return "";
        }
        chatHistoryBuilder.append(data);
        return data;
    }

    /**
     * 每个工具调用只在首次收到对应 id 时展示一次选择提示。
     *
     * 同一个调用的参数可能产生多个分片，按 id 去重可以避免连续出现重复提示。极少数
     * 模型接口不提供 id 时使用工具名兜底，至少保证响应不会完全缺少进度信息。
     */
    private String handleToolRequest(String chunk, Set<String> seenToolRequestIds) {
        ToolRequestMessage message = JSONUtil.toBean(chunk, ToolRequestMessage.class);
        String requestKey = StrUtil.blankToDefault(message.getId(), message.getName());
        if (StrUtil.isBlank(requestKey) || !seenToolRequestIds.add(requestKey)) {
            return "";
        }

        BaseTool tool = findTool(message.getName());
        if (tool == null) {
            String toolName = StrUtil.blankToDefault(message.getName(), "未知工具");
            log.warn("收到未注册的工具请求：{}", toolName);
            return String.format("\n\n[选择工具] %s\n\n", toolName);
        }
        return tool.generateToolRequestResponse();
    }

    /**
     * 使用完整参数生成工具执行详情，并将相同文本同时发送给前端和写入历史记录。
     */
    private String handleToolExecuted(String chunk, StringBuilder chatHistoryBuilder) {
        ToolExecutedMessage message = JSONUtil.toBean(chunk, ToolExecutedMessage.class);
        JSONObject arguments = parseToolArguments(message);
        BaseTool tool = findTool(message.getName());

        String result;
        if (tool == null) {
            String toolName = StrUtil.blankToDefault(message.getName(), "未知工具");
            log.warn("收到未注册工具的执行结果：{}", toolName);
            result = "[工具调用] " + toolName;
        } else {
            try {
                result = tool.generateToolExecutedResult(arguments);
            } catch (RuntimeException e) {
                log.warn("生成工具执行展示信息失败，工具：{}", message.getName(), e);
                result = "[工具调用] " + tool.getDisplayName() + "（参数无法展示）";
            }
        }

        String output = String.format("\n\n%s\n\n", result);
        chatHistoryBuilder.append(output);
        return output;
    }

    /**
     * 将执行完成事件中的参数解析成 JSON；异常参数只影响详情展示，不中断整个响应流。
     */
    private JSONObject parseToolArguments(ToolExecutedMessage message) {
        if (StrUtil.isBlank(message.getArguments())) {
            return new JSONObject();
        }
        try {
            return JSONUtil.parseObj(message.getArguments());
        } catch (RuntimeException e) {
            log.warn("工具执行参数不是有效 JSON，工具：{}", message.getName(), e);
            return new JSONObject();
        }
    }

    /** 工具名称为空时直接返回 null，避免不可变 Map 对 null 键的限制。 */
    private BaseTool findTool(String toolName) {
        return StrUtil.isBlank(toolName) ? null : toolManager.getTool(toolName);
    }

    /**
     * 流正常结束后保存整理好的完整回复；“选择工具”提示不会写入历史记录。
     */
    private void saveCompletedResponse(ChatHistoryService chatHistoryService,
                                       long appId,
                                       Long userId,
                                       String response) {
        if (StrUtil.isBlank(response)) {
            return;
        }
        boolean saved = chatHistoryService.addChatMessage(
                appId,
                response,
                ChatHistoryMessageTypeEnum.AI.getValue(),
                userId
        );
        ThrowUtils.throwIf(!saved, ErrorCode.OPERATION_ERROR, "AI 回复保存失败");
    }

    /**
     * 尽力保存失败说明，数据库异常只记录日志，不覆盖真正的流处理异常。
     */
    private void saveFailureResponse(ChatHistoryService chatHistoryService,
                                     long appId,
                                     Long userId,
                                     Throwable error) {
        String detail = StrUtil.blankToDefault(error.getMessage(), "未知错误");
        if (detail.length() > 500) {
            detail = detail.substring(0, 500);
        }
        try {
            chatHistoryService.addChatMessage(
                    appId,
                    "AI 回复失败：" + detail,
                    ChatHistoryMessageTypeEnum.AI.getValue(),
                    userId
            );
        } catch (Exception saveException) {
            log.error("保存 AI 失败消息时出现异常，appId={}", appId, saveException);
        }
    }
}
