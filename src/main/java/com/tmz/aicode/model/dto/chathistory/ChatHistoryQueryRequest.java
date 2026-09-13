package com.tmz.aicode.model.dto.chathistory;

import com.tmz.aicode.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 查询对话历史时接收的筛选与分页参数。
 *
 * 普通应用会话只使用 appId、pageSize 和 lastCreateTime。其余字段主要供管理员在后台
 * 筛选全站记录使用。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ChatHistoryQueryRequest extends PageRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 消息 id，用于精确查找。 */
    private Long id;

    /** 消息关键字，查询时使用模糊匹配。 */
    private String message;

    /** 消息发送方，只接受 user 或 ai。 */
    private String messageType;

    /** 消息所属应用 id。 */
    private Long appId;

    /** 发起会话的用户 id。 */
    private Long userId;

    /**
     * 当前页面最早一条消息的创建时间。
     *
     * 加载更多时只查询早于这个时间的记录，避免新消息插入后造成页码偏移和重复数据。
     */
    private LocalDateTime lastCreateTime;
}
