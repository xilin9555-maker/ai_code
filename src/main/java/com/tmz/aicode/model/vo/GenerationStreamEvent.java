package com.tmz.aicode.model.vo;

import com.tmz.aicode.model.dto.build.BuildProgress;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 发送给聊天页的生成事件。
 *
 * event 决定 SSE 事件名称，data 是该事件对应的 JSON 数据。普通回复仍保持原有 d 字段，
 * 构建事件则直接携带阶段、状态和进度，避免前端通过文本内容猜测当前流程。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GenerationStreamEvent {

    public static final String MESSAGE_EVENT = "message";

    private String event;

    private Object data;

    /** 把可展示文本包装为兼容现有前端的普通消息。 */
    public static GenerationStreamEvent message(String content) {
        return new GenerationStreamEvent(MESSAGE_EVENT, Map.of("d", content));
    }

    /** 把构建进度包装为具名事件。 */
    public static GenerationStreamEvent build(BuildProgress progress) {
        return new GenerationStreamEvent(progress.getEvent(), progress);
    }
}
