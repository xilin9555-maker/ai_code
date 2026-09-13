package com.tmz.aicode.ai.model.message;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * AI 发起的工具调用请求消息。
 *
 * 工具参数可能随着模型输出逐步补全，因此同一个调用 id 后续可能产生多条此类消息。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ToolRequestMessage extends StreamMessage {

    /**
     * 本次工具调用的唯一标识，用于关联后续的参数片段和执行结果。
     */
    private String id;

    /**
     * AI 选择调用的工具名称，例如 {@code writeFile}。
     */
    private String name;

    /**
     * 工具调用参数的 JSON 字符串；在流式阶段可能暂时还不完整。
     */
    private String arguments;

    /**
     * 将 LangChain4j 的工具调用请求转换为统一的接口消息。
     *
     * @param toolExecutionRequest 模型当前返回的工具调用请求
     */
    public ToolRequestMessage(ToolExecutionRequest toolExecutionRequest) {
        super(StreamMessageTypeEnum.TOOL_REQUEST.getValue());
        this.id = toolExecutionRequest.id();
        this.name = toolExecutionRequest.name();
        this.arguments = toolExecutionRequest.arguments();
    }
}
