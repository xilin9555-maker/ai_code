package com.tmz.aicode.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.tmz.aicode.constant.UserConstant;
import com.tmz.aicode.exception.BusinessException;
import com.tmz.aicode.exception.ErrorCode;
import com.tmz.aicode.exception.ThrowUtils;
import com.tmz.aicode.mapper.ChatHistoryMapper;
import com.tmz.aicode.model.dto.chathistory.ChatHistoryQueryRequest;
import com.tmz.aicode.model.entity.App;
import com.tmz.aicode.model.entity.ChatHistory;
import com.tmz.aicode.model.entity.User;
import com.tmz.aicode.model.enums.ChatHistoryMessageTypeEnum;
import com.tmz.aicode.service.AppService;
import com.tmz.aicode.service.ChatHistoryService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 对话历史业务服务实现。
 */
@Slf4j
@Service
public class ChatHistoryServiceImpl
        extends ServiceImpl<ChatHistoryMapper, ChatHistory>
        implements ChatHistoryService {

    /**
     * 管理员查询允许使用的排序字段。
     *
     * 排序字段会参与 SQL 生成，所以必须经过固定白名单检查，不能直接采用客户端传值。
     */
    private static final Set<String> CHAT_HISTORY_SORT_FIELDS = Set.of(
            "id", "messageType", "appId", "userId", "createTime", "updateTime"
    );

    private final AppService appService;

    /**
     * AppService 在生成和删除应用时会调用本服务，而这里需要读取应用来校验查看权限。
     * Lazy 让 Spring 在真正查询时再解析 AppService，避免两个服务在启动阶段互相等待。
     */
    public ChatHistoryServiceImpl(@Lazy AppService appService) {
        this.appService = appService;
    }

    /**
     * 校验消息的关键字段后写入数据库。
     *
     * message 不做 trim 后再保存，因为生成代码中的换行和前导空格属于有效内容；只用
     * isBlank 判断它是否完全为空。消息类型必须来自枚举，防止出现无法识别的发送方。
     */
    @Override
    public boolean addChatMessage(Long appId, String message, String messageType, Long userId) {
        ThrowUtils.throwIf(appId == null || appId <= 0,
                ErrorCode.PARAMS_ERROR, "应用 id 不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(message),
                ErrorCode.PARAMS_ERROR, "消息内容不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(messageType),
                ErrorCode.PARAMS_ERROR, "消息类型不能为空");
        ThrowUtils.throwIf(userId == null || userId <= 0,
                ErrorCode.PARAMS_ERROR, "用户 id 不能为空");

        ChatHistoryMessageTypeEnum type =
                ChatHistoryMessageTypeEnum.getEnumByValue(messageType);
        ThrowUtils.throwIf(type == null,
                ErrorCode.PARAMS_ERROR, "不支持的消息类型：" + messageType);

        ChatHistory chatHistory = ChatHistory.builder()
                .appId(appId)
                .message(message)
                .messageType(type.getValue())
                .userId(userId)
                .build();
        return this.save(chatHistory);
    }

    /**
     * 按 appId 清理该应用的全部历史记录。
     */
    @Override
    public boolean deleteByAppId(Long appId) {
        ThrowUtils.throwIf(appId == null || appId <= 0,
                ErrorCode.PARAMS_ERROR, "应用 id 不能为空");
        return this.remove(QueryWrapper.create().eq("appId", appId));
    }

    /**
     * 从 MySQL 恢复最近的对话到 LangChain4j 消息窗口。
     *
     * 数据库查询按照创建时间从新到旧执行，并从偏移量 1 开始，排除生成流程刚刚保存的
     * 当前用户消息。查询完成后再反转列表，确保模型看到的顺序与真实对话一致：先看到较早
     * 的问题和回答，再看到较新的内容。写入前清空 Redis 中的旧值，避免缓存重建时重复追加。
     */
    @Override
    public int loadChatHistoryToMemory(Long appId,
                                       MessageWindowChatMemory chatMemory,
                                       int maxCount) {
        try {
            QueryWrapper queryWrapper = QueryWrapper.create()
                    .eq(ChatHistory::getAppId, appId)
                    .orderBy(ChatHistory::getCreateTime, false)
                    // 最新一条是本轮已经落库的用户消息，从下一条开始读取历史上下文。
                    .limit(1, maxCount);
            List<ChatHistory> historyList = this.list(queryWrapper);
            if (CollUtil.isEmpty(historyList)) {
                return 0;
            }

            // 查询结果是从新到旧，写入模型记忆前需要调整成正常的对话时间顺序。
            historyList = historyList.reversed();
            chatMemory.clear();

            int loadedCount = 0;
            for (ChatHistory history : historyList) {
                if (ChatHistoryMessageTypeEnum.USER.getValue().equals(history.getMessageType())) {
                    chatMemory.add(UserMessage.from(history.getMessage()));
                    loadedCount++;
                } else if (ChatHistoryMessageTypeEnum.AI.getValue().equals(history.getMessageType())) {
                    chatMemory.add(AiMessage.from(history.getMessage()));
                    loadedCount++;
                }
            }
            log.info("已恢复应用的历史对话，应用 id：{}，消息数：{}", appId, loadedCount);
            return loadedCount;
        } catch (Exception exception) {
            // 历史恢复失败时保留本轮生成能力，同时记录原因供后续排查。
            log.error("恢复应用历史对话失败，应用 id：{}", appId, exception);
            return 0;
        }
    }

    /**
     * 将非空查询参数转换为数据库条件，并默认按创建时间从新到旧排列。
     */
    @Override
    public QueryWrapper getQueryWrapper(ChatHistoryQueryRequest queryRequest) {
        if (queryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }

        QueryWrapper queryWrapper = QueryWrapper.create();
        if (queryRequest.getId() != null && queryRequest.getId() > 0) {
            queryWrapper.eq("id", queryRequest.getId());
        }
        if (StrUtil.isNotBlank(queryRequest.getMessage())) {
            queryWrapper.like("message", queryRequest.getMessage().trim());
        }
        if (StrUtil.isNotBlank(queryRequest.getMessageType())) {
            ChatHistoryMessageTypeEnum type =
                    ChatHistoryMessageTypeEnum.getEnumByValue(queryRequest.getMessageType());
            ThrowUtils.throwIf(type == null,
                    ErrorCode.PARAMS_ERROR, "消息类型不合法");
            queryWrapper.eq("messageType", type.getValue());
        }
        if (queryRequest.getAppId() != null && queryRequest.getAppId() > 0) {
            queryWrapper.eq("appId", queryRequest.getAppId());
        }
        if (queryRequest.getUserId() != null && queryRequest.getUserId() > 0) {
            queryWrapper.eq("userId", queryRequest.getUserId());
        }

        // 游标表示当前已加载到的最早时间，下一页只需要读取它之前的消息。
        if (queryRequest.getLastCreateTime() != null) {
            queryWrapper.lt("createTime", queryRequest.getLastCreateTime());
        }

        String sortField = queryRequest.getSortField();
        String sortOrder = queryRequest.getSortOrder();
        if (StrUtil.isNotBlank(sortField)) {
            ThrowUtils.throwIf(!CHAT_HISTORY_SORT_FIELDS.contains(sortField),
                    ErrorCode.PARAMS_ERROR, "排序字段不合法");
            ThrowUtils.throwIf(!"ascend".equals(sortOrder) && !"descend".equals(sortOrder),
                    ErrorCode.PARAMS_ERROR, "排序方式不合法");
            queryWrapper.orderBy(sortField, "ascend".equals(sortOrder));
        } else {
            queryWrapper.orderBy("createTime", false);
        }
        return queryWrapper;
    }

    /**
     * 校验应用和访问者后执行游标查询。
     *
     * 页码始终从 1 开始，因为翻页位置由 lastCreateTime 决定。每次最多读取 50 条，既满足
     * 页面“加载更多”的需要，也避免一次把很长的 AI 回复历史全部拉入内存。
     */
    @Override
    public Page<ChatHistory> listAppChatHistoryByPage(Long appId,
                                                      int pageSize,
                                                      LocalDateTime lastCreateTime,
                                                      User loginUser) {
        ThrowUtils.throwIf(appId == null || appId <= 0,
                ErrorCode.PARAMS_ERROR, "应用 id 不能为空");
        ThrowUtils.throwIf(pageSize <= 0 || pageSize > 50,
                ErrorCode.PARAMS_ERROR, "每次只能查询 1 到 50 条消息");
        ThrowUtils.throwIf(loginUser == null || loginUser.getId() == null,
                ErrorCode.NOT_LOGIN_ERROR);

        App app = appService.getById(appId);
        ThrowUtils.throwIf(app == null,
                ErrorCode.NOT_FOUND_ERROR, "应用不存在或已经被删除");
        boolean isCreator = Objects.equals(app.getUserId(), loginUser.getId());
        boolean isAdmin = UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole());
        ThrowUtils.throwIf(!isCreator && !isAdmin,
                ErrorCode.NO_AUTH_ERROR, "无权查看该应用的对话历史");

        ChatHistoryQueryRequest queryRequest = new ChatHistoryQueryRequest();
        queryRequest.setAppId(appId);
        queryRequest.setLastCreateTime(lastCreateTime);
        return this.page(Page.of(1, pageSize), getQueryWrapper(queryRequest));
    }
}
