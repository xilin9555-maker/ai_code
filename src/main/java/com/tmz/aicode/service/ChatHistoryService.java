package com.tmz.aicode.service;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.tmz.aicode.model.dto.chathistory.ChatHistoryQueryRequest;
import com.tmz.aicode.model.entity.ChatHistory;
import com.tmz.aicode.model.entity.User;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

import java.time.LocalDateTime;

/**
 * 对话历史业务服务。
 *
 * 这里集中处理消息落库、应用数据隔离、查看权限和游标查询，让生成服务与控制器无需
 * 重复拼装对话历史的数据库操作。
 */
public interface ChatHistoryService extends IService<ChatHistory> {

    /**
     * 保存一条已经校验过归属关系的对话消息。
     *
     * @param appId 消息所属应用 id
     * @param message 完整消息内容
     * @param messageType 消息发送方，取值为 user 或 ai
     * @param userId 发起本轮对话的用户 id
     * @return 数据库写入成功时返回 true
     */
    boolean addChatMessage(Long appId, String message, String messageType, Long userId);

    /**
     * 删除指定应用的全部对话历史。
     *
     * @param appId 被删除的应用 id
     * @return 实际删除到记录时返回 true；没有历史记录时返回 false
     */
    boolean deleteByAppId(Long appId);

    /**
     * 将数据库中最近的对话记录恢复到指定的模型记忆窗口。
     *
     * 调用方会在本轮用户消息已经落库后执行此方法，因此查询会跳过数据库中最新一条消息，
     * 防止 LangChain4j 随后自动加入相同用户消息时产生重复上下文。
     *
     * @param appId 需要恢复对话记忆的应用 id
     * @param chatMemory 当前应用绑定的消息窗口
     * @param maxCount 最多恢复的历史消息数量
     * @return 实际加入记忆窗口的消息数量；加载失败或没有历史时返回 0
     */
    int loadChatHistoryToMemory(Long appId,
                                MessageWindowChatMemory chatMemory,
                                int maxCount);

    /**
     * 根据请求中的非空字段构造查询条件。
     *
     * @param queryRequest 筛选、游标与排序参数
     * @return 可交给 MyBatis-Flex 执行的查询条件
     */
    QueryWrapper getQueryWrapper(ChatHistoryQueryRequest queryRequest);

    /**
     * 按创建时间游标加载某个应用的历史消息。
     *
     * @param appId 需要查看的应用 id
     * @param pageSize 本次最多加载的消息数，范围为 1 到 50
     * @param lastCreateTime 上一页最早消息的时间，首次加载时传 null
     * @param loginUser 当前登录用户，用于检查创建者或管理员身份
     * @return 按创建时间倒序排列的一页消息
     */
    Page<ChatHistory> listAppChatHistoryByPage(Long appId,
                                               int pageSize,
                                               LocalDateTime lastCreateTime,
                                               User loginUser);
}
