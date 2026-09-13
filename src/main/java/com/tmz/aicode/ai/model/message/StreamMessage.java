package com.tmz.aicode.ai.model.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 流式响应消息的公共基类。
 *
 * 后端会通过同一条 SSE 连接发送普通文本、工具调用过程和工具执行结果。
 * type 字段用于告诉接收方当前消息的具体类别，前端可以据此选择不同的展示方式。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class StreamMessage {

    /**
     * 消息类型，对应 {@link StreamMessageTypeEnum} 中定义的稳定值。
     */
    private String type;
}
