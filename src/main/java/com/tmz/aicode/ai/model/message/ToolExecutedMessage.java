package com.tmz.aicode.ai.model.message;

import dev.langchain4j.service.tool.ToolExecution;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 工具执行完成消息。
 *
 * 该消息保留完整的调用参数和执行结果，可用于前端展示执行状态，也方便后续整理对话记录。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ToolExecutedMessage extends StreamMessage {

    /**
     * 工具调用的唯一标识，与对应的工具请求消息保持一致。
     */
    private String id;

    /**
     * 已经执行的工具名称。
     */
    private String name;

    /**
     * 工具执行时使用的完整 JSON 参数。
     */
    private String arguments;

    /**
     * 工具执行后返回的文本结果。
     */
    private String result;

    /**
     * 将 LangChain4j 的工具执行记录转换为统一的接口消息。
     *
     * @param toolExecution 已完成的工具执行记录
     */
    public ToolExecutedMessage(ToolExecution toolExecution) {
        super(StreamMessageTypeEnum.TOOL_EXECUTED.getValue());
        this.id = toolExecution.request().id();
        this.name = toolExecution.request().name();
        this.arguments = toolExecution.request().arguments();
        this.result = toolExecution.result();
    }
}
