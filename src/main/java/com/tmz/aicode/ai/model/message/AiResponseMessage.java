package com.tmz.aicode.ai.model.message;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * AI 普通文本响应消息。
 *
 * 每次收到一段流式文本时，可以把文本放入 data，并以 ai_response 类型发送给前端。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class AiResponseMessage extends StreamMessage {

    /**
     * 本次收到的增量文本，不代表整次回答的完整内容。
     */
    private String data;

    /**
     * 创建一条 AI 文本响应，并自动设置消息类型。
     *
     * @param data 当前收到的文本片段
     */
    public AiResponseMessage(String data) {
        super(StreamMessageTypeEnum.AI_RESPONSE.getValue());
        this.data = data;
    }
}
