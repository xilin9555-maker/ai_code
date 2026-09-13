package com.tmz.aicode.ai.model.message;

import lombok.Getter;

/**
 * 流式响应支持的消息类型。
 *
 * value 会作为接口中的类型标识传给前端，text 用于日志、调试页面等需要中文说明的场景。
 */
@Getter
public enum StreamMessageTypeEnum {

    /**
     * AI 输出的普通文本片段。
     */
    AI_RESPONSE("ai_response", "AI 响应"),

    /**
     * AI 正在生成的工具调用请求，其中可能包含逐步补全的文件路径和代码参数。
     */
    TOOL_REQUEST("tool_request", "工具请求"),

    /**
     * 工具已经执行结束，消息中包含完整参数和执行结果。
     */
    TOOL_EXECUTED("tool_executed", "工具执行结果");

    /**
     * 提供给程序和前端判断的稳定标识。
     */
    private final String value;

    /**
     * 便于阅读和展示的类型说明。
     */
    private final String text;

    StreamMessageTypeEnum(String value, String text) {
        this.value = value;
        this.text = text;
    }

    /**
     * 根据接口中的类型值查找对应枚举。
     *
     * @param value 消息类型值，例如 {@code ai_response}
     * @return 匹配的枚举；值为空或无法识别时返回 {@code null}
     */
    public static StreamMessageTypeEnum getEnumByValue(String value) {
        for (StreamMessageTypeEnum typeEnum : values()) {
            if (typeEnum.value.equals(value)) {
                return typeEnum;
            }
        }
        return null;
    }
}
