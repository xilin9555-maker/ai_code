package com.tmz.aicode.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.keygen.KeyGenerators;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 一条应用对话消息的数据库记录。
 *
 * 用户消息和 AI 消息使用同一种实体保存，通过 messageType 区分发送方。appId 用来隔离
 * 不同应用的会话，userId 记录发起这次生成的用户，便于后续审计和后台查询。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("chat_history")
public class ChatHistory implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 消息唯一标识，由服务端使用雪花算法生成。
     */
    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private Long id;

    /**
     * 完整消息内容。字段在数据库中使用 text，能够保存较长的生成结果。
     */
    @Column("message")
    private String message;

    /**
     * 消息发送方，值来自 ChatHistoryMessageTypeEnum，例如 user 或 ai。
     */
    @Column("messageType")
    private String messageType;

    /**
     * 消息所属的应用 id。同一个应用的历史记录都使用相同的 appId。
     */
    @Column("appId")
    private Long appId;

    /**
     * 发起这轮对话的用户 id。AI 回复也沿用同一用户 id，表示回复属于谁的会话。
     */
    @Column("userId")
    private Long userId;

    /**
     * 消息创建时间，由数据库写入，也是加载更早记录时使用的游标。
     */
    @Column("createTime")
    private LocalDateTime createTime;

    /**
     * 记录最近一次更新时间，由数据库自动维护。
     */
    @Column("updateTime")
    private LocalDateTime updateTime;

    /**
     * 逻辑删除标记：0 表示有效，1 表示已经删除。
     */
    @Column(value = "isDelete", isLogicDelete = true)
    private Integer isDelete;
}
