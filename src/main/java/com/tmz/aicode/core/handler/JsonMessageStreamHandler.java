package com.tmz.aicode.core.handler;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.tmz.aicode.ai.model.message.AiResponseMessage;
import com.tmz.aicode.ai.model.message.StreamMessage;
import com.tmz.aicode.ai.model.message.StreamMessageTypeEnum;
import com.tmz.aicode.ai.model.message.ToolExecutedMessage;
import com.tmz.aicode.ai.model.message.ToolRequestMessage;
import com.tmz.aicode.constant.AppConstant;
import com.tmz.aicode.core.builder.VueProjectBuilder;
import com.tmz.aicode.exception.ErrorCode;
import com.tmz.aicode.exception.ThrowUtils;
import com.tmz.aicode.model.entity.User;
import com.tmz.aicode.model.enums.ChatHistoryMessageTypeEnum;
import com.tmz.aicode.service.ChatHistoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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

    private final VueProjectBuilder vueProjectBuilder;

    /**
     * 注入 Vue 项目构建器，在工程源码生成完整后启动后台构建。
     *
     * @param vueProjectBuilder 负责安装依赖并生成 dist 目录的构建器
     */
    public JsonMessageStreamHandler(VueProjectBuilder vueProjectBuilder) {
        this.vueProjectBuilder = vueProjectBuilder;
    }

    /**
     * 解析统一消息、生成前端文本，并在流结束后保存完整对话。
     *
     * 工具参数通常分成很多片段到达，而且文件内容就包含在这些参数中。处理器按工具调用
     * 累积 JSON 片段，并及时提取 content 新增的部分返回前端，避免等整个文件写完后才
     * 一次性显示。announcedToolNames 还会保证同一种工具在本轮响应中只提示一次。
     *
     * @param originFlux 门面输出的统一 JSON 消息流
     * @param chatHistoryService 对话历史服务
     * @param appId 当前应用 id
     * @param loginUser 发起生成的登录用户
     * @return 已转换为可展示文本的响应流
     */
    public Flux<String> handle(Flux<String> originFlux,
                               ChatHistoryService chatHistoryService,
                               long appId,
                               User loginUser) {
        return Flux.defer(() -> {
            StringBuilder chatHistoryBuilder = new StringBuilder();
            Set<String> announcedToolNames = new HashSet<>();
            Map<String, ToolStreamState> toolStates = new HashMap<>();
            return originFlux
                    .map(chunk -> handleJsonMessageChunk(
                            chunk,
                            chatHistoryBuilder,
                            announcedToolNames,
                            toolStates
                    ))
                    // 只过滤当前片段没有产生可展示增量的情况。
                    .filter(StrUtil::isNotEmpty)
                    .doOnComplete(() -> handleCompletedResponse(
                            chatHistoryService, appId, loginUser, chatHistoryBuilder
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
     * 流正常结束后保存完整回复，并启动当前 Vue 工程的后台构建。
     *
     * 文件写入工具只有在流完成时才确定全部调用已经结束，因此这里是开始构建最早且可靠
     * 的时机。构建器在虚拟线程中工作，本方法不会等待 npm 命令执行完毕。
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
        File projectDir = new File(AppConstant.CODE_OUTPUT_ROOT_DIR, "vue_project_" + appId);
        vueProjectBuilder.buildProjectAsync(projectDir.getAbsolutePath());
    }

    /**
     * 根据 type 字段处理一个内部 JSON 消息。
     *
     * AI 文本会直接进入前端和历史记录；工具请求负责持续输出正在生成的文件内容；
     * 工具完成消息负责补齐前端内容，同时向历史记录写入结构完整的代码块。
     */
    private String handleJsonMessageChunk(String chunk,
                                          StringBuilder chatHistoryBuilder,
                                          Set<String> announcedToolNames,
                                          Map<String, ToolStreamState> toolStates) {
        StreamMessage streamMessage = JSONUtil.toBean(chunk, StreamMessage.class);
        StreamMessageTypeEnum type = StreamMessageTypeEnum.getEnumByValue(streamMessage.getType());
        if (type == null) {
            log.warn("收到无法识别的流式消息类型：{}", streamMessage.getType());
            return "";
        }

        return switch (type) {
            case AI_RESPONSE -> handleAiResponse(chunk, chatHistoryBuilder);
            case TOOL_REQUEST -> handleToolRequest(
                    chunk, chatHistoryBuilder, announcedToolNames, toolStates
            );
            case TOOL_EXECUTED -> handleToolExecuted(
                    chunk, chatHistoryBuilder, toolStates
            );
        };
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
     * 增量处理工具参数，并把刚生成的文件内容立即传给前端。
     *
     * 每个文件都有独立的调用 id，如果按 id 去重，生成十个文件仍会显示十次相同提示。
     * 选择提示按工具名称去重；参数片段则按调用 id 累积，从尚未闭合的 JSON 字符串中
     * 解码 content。这样模型每生成一小段代码，前端都能收到对应增量。
     */
    private String handleToolRequest(String chunk,
                                     StringBuilder chatHistoryBuilder,
                                     Set<String> announcedToolNames,
                                     Map<String, ToolStreamState> toolStates) {
        ToolRequestMessage message = JSONUtil.toBean(chunk, ToolRequestMessage.class);
        String toolName = message.getName();
        StringBuilder output = new StringBuilder();
        if (StrUtil.isNotBlank(toolName) && announcedToolNames.add(toolName)) {
            // 选择提示只用于实时反馈，不写入需要长期保存的对话历史。
            output.append("\n\n[选择工具] 写入文件\n\n");
        }

        String toolKey = getToolKey(message.getId(), toolName);
        if (StrUtil.isBlank(toolKey) || message.getArguments() == null) {
            return output.toString();
        }
        ToolStreamState state = toolStates.computeIfAbsent(toolKey, ignored -> new ToolStreamState());
        state.arguments.append(message.getArguments());
        appendNewToolContent(state, output);
        return output.toString();
    }

    /**
     * 工具完成时补齐尚未输出的内容并关闭 Markdown 代码块。
     *
     * 正常情况下绝大部分代码已经由工具参数分片实时输出，这里只发送最后尚未抵达的少量
     * 内容。如果上游没有提供分片，则退化为一次性输出完整文件，保证结果不会丢失。
     */
    private String handleToolExecuted(String chunk,
                                      StringBuilder chatHistoryBuilder,
                                      Map<String, ToolStreamState> toolStates) {
        ToolExecutedMessage message = JSONUtil.toBean(chunk, ToolExecutedMessage.class);
        JSONObject arguments = JSONUtil.parseObj(message.getArguments());
        String relativeFilePath = arguments.getStr("relativeFilePath");
        if (StrUtil.isBlank(relativeFilePath)) {
            throw new IllegalArgumentException("writeFile 工具缺少 relativeFilePath 参数");
        }
        String content = arguments.getStr("content");
        if (content == null) {
            content = "";
        }
        String toolKey = getToolKey(message.getId(), message.getName());
        ToolStreamState state = toolStates.remove(toolKey);
        if (state == null) {
            state = new ToolStreamState();
        }

        StringBuilder output = new StringBuilder();
        openCodeBlockIfNecessary(state, relativeFilePath, output);
        int emittedLength = Math.min(state.emittedContentLength, content.length());
        if (content.length() > emittedLength) {
            output.append(content.substring(emittedLength));
        }
        output.append("\n```\n\n");

        // 历史记录使用工具完成事件中的完整参数重新组装，避免并行参数流造成围栏交错。
        String language = StrUtil.blankToDefault(FileUtil.getSuffix(relativeFilePath), "text");
        chatHistoryBuilder.append("\n\n[工具调用] 写入文件 ")
                .append(relativeFilePath)
                .append("\n```")
                .append(language)
                .append('\n')
                .append(content)
                .append("\n```\n\n");
        return output.toString();
    }

    /**
     * 从当前累计参数中提取路径与文件内容，只输出相对于上一次新增的代码。
     */
    private void appendNewToolContent(ToolStreamState state, StringBuilder output) {
        String accumulatedArguments = state.arguments.toString();
        JsonStringPrefix pathPrefix = decodeJsonStringPrefix(
                accumulatedArguments, "relativeFilePath"
        );
        JsonStringPrefix contentPrefix = decodeJsonStringPrefix(accumulatedArguments, "content");
        if (pathPrefix == null || !pathPrefix.complete() || contentPrefix == null) {
            return;
        }

        openCodeBlockIfNecessary(state, pathPrefix.value(), output);
        String decodedContent = contentPrefix.value();
        if (decodedContent.length() <= state.emittedContentLength) {
            return;
        }
        String delta = decodedContent.substring(state.emittedContentLength);
        state.emittedContentLength = decodedContent.length();
        output.append(delta);
    }

    /**
     * 第一次取得完整文件路径时输出文件标题和代码块起始标记。
     */
    private void openCodeBlockIfNecessary(ToolStreamState state,
                                          String relativeFilePath,
                                          StringBuilder output) {
        if (state.codeBlockOpened) {
            return;
        }
        String language = StrUtil.blankToDefault(FileUtil.getSuffix(relativeFilePath), "text");
        String header = "\n\n[工具调用] 写入文件 " + relativeFilePath
                + "\n```" + language + "\n";
        state.codeBlockOpened = true;
        output.append(header);
    }

    /**
     * 工具 id 在一次调用中保持稳定；极少数兼容接口没有返回 id 时使用工具名兜底。
     */
    private String getToolKey(String toolId, String toolName) {
        return StrUtil.isNotBlank(toolId) ? toolId : toolName;
    }

    /**
     * 从尚未接收完整的 JSON 中解码某个字符串属性。
     *
     * 这里不能直接调用 JSONUtil，因为流式阶段的字符串通常还缺少结尾引号和右花括号。
     * 方法会安全处理换行、引号、反斜杠与 Unicode 转义；遇到尚未接收完整的转义序列时
     * 暂停在完整字符之前，下一片数据到达后再继续输出。
     */
    private JsonStringPrefix decodeJsonStringPrefix(String json, String propertyName) {
        String key = "\"" + propertyName + "\"";
        int keyIndex = json.indexOf(key);
        if (keyIndex < 0) {
            return null;
        }

        int cursor = keyIndex + key.length();
        while (cursor < json.length() && Character.isWhitespace(json.charAt(cursor))) {
            cursor++;
        }
        if (cursor >= json.length() || json.charAt(cursor) != ':') {
            return null;
        }
        cursor++;
        while (cursor < json.length() && Character.isWhitespace(json.charAt(cursor))) {
            cursor++;
        }
        if (cursor >= json.length() || json.charAt(cursor) != '"') {
            return null;
        }
        cursor++;

        StringBuilder decoded = new StringBuilder();
        while (cursor < json.length()) {
            char current = json.charAt(cursor++);
            if (current == '"') {
                return new JsonStringPrefix(decoded.toString(), true);
            }
            if (current != '\\') {
                decoded.append(current);
                continue;
            }
            if (cursor >= json.length()) {
                break;
            }

            char escaped = json.charAt(cursor++);
            switch (escaped) {
                case '"', '\\', '/' -> decoded.append(escaped);
                case 'b' -> decoded.append('\b');
                case 'f' -> decoded.append('\f');
                case 'n' -> decoded.append('\n');
                case 'r' -> decoded.append('\r');
                case 't' -> decoded.append('\t');
                case 'u' -> {
                    if (cursor + 4 > json.length()) {
                        return new JsonStringPrefix(decoded.toString(), false);
                    }
                    String hex = json.substring(cursor, cursor + 4);
                    try {
                        decoded.append((char) Integer.parseInt(hex, 16));
                    } catch (NumberFormatException ignored) {
                        return new JsonStringPrefix(decoded.toString(), false);
                    }
                    cursor += 4;
                }
                default -> {
                    // 非法转义交给完整工具参数校验处理，流式展示先停在可靠内容处。
                    return new JsonStringPrefix(decoded.toString(), false);
                }
            }
        }
        return new JsonStringPrefix(decoded.toString(), false);
    }

    /** 保存一个工具调用在流式阶段的累计参数和已输出位置。 */
    private static class ToolStreamState {
        private final StringBuilder arguments = new StringBuilder();
        private int emittedContentLength;
        private boolean codeBlockOpened;
    }

    /** 字符串属性当前可以安全解码的内容，以及它的结束引号是否已经到达。 */
    private record JsonStringPrefix(String value, boolean complete) {
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
