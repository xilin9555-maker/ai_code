package com.tmz.aicode.ai.model.message;

import com.tmz.aicode.model.dto.build.BuildProgress;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 在 Vue 内部消息流中传递的构建进度消息。
 *
 * 代码生成文本和构建进度继续共用一条流，但通过 type 明确区分。后续处理器可以把构建
 * 进度转换为具名 SSE 事件，同时避免把这些临时状态保存为 AI 对话内容。
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class BuildProgressMessage extends StreamMessage {

    private BuildProgress data;

    public BuildProgressMessage(BuildProgress data) {
        super(StreamMessageTypeEnum.BUILD_PROGRESS.getValue());
        this.data = data;
    }
}
