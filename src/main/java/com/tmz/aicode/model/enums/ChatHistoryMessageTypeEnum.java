package com.tmz.aicode.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

/**
 * 对话历史支持的消息发送方。
 *
 * text 适合页面展示，value 作为稳定值写入数据库。业务代码统一通过枚举取值，避免在
 * 多处手写 user、ai 时出现拼写不一致。
 */
@Getter
public enum ChatHistoryMessageTypeEnum {

    /** 用户向应用提交的需求或修改意见。 */
    USER("用户", "user"),

    /** AI 返回的完整回复或生成失败说明。 */
    AI("AI", "ai");

    private final String text;

    private final String value;

    ChatHistoryMessageTypeEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据数据库或请求中的值查找消息类型。
     *
     * @param value 消息类型值，例如 user 或 ai
     * @return 对应枚举；值为空或无法识别时返回 null
     */
    public static ChatHistoryMessageTypeEnum getEnumByValue(String value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        for (ChatHistoryMessageTypeEnum messageType : values()) {
            if (messageType.value.equals(value)) {
                return messageType;
            }
        }
        return null;
    }
}
