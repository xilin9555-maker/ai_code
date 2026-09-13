package com.tmz.aicode.mapper;

import com.mybatisflex.core.BaseMapper;
import com.tmz.aicode.model.entity.ChatHistory;

/**
 * 对话历史数据访问层。
 *
 * BaseMapper 已经提供新增、分页查询和按条件删除等通用操作，目前不需要额外手写 SQL。
 */
public interface ChatHistoryMapper extends BaseMapper<ChatHistory> {
}
