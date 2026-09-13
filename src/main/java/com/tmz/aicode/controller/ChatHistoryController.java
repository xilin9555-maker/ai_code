package com.tmz.aicode.controller;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.tmz.aicode.annotation.AuthCheck;
import com.tmz.aicode.common.BaseResponse;
import com.tmz.aicode.common.ResultUtils;
import com.tmz.aicode.constant.UserConstant;
import com.tmz.aicode.exception.ErrorCode;
import com.tmz.aicode.exception.ThrowUtils;
import com.tmz.aicode.model.dto.chathistory.ChatHistoryQueryRequest;
import com.tmz.aicode.model.entity.ChatHistory;
import com.tmz.aicode.model.entity.User;
import com.tmz.aicode.service.ChatHistoryService;
import com.tmz.aicode.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 对话历史查询接口。
 *
 * 普通接口只允许应用创建者查看自己的对话，管理员接口提供全站分页筛选能力。
 */
@RestController
@RequestMapping("/chatHistory")
public class ChatHistoryController {

    private final ChatHistoryService chatHistoryService;

    private final UserService userService;

    public ChatHistoryController(ChatHistoryService chatHistoryService,
                                 UserService userService) {
        this.chatHistoryService = chatHistoryService;
        this.userService = userService;
    }

    /**
     * 使用时间游标加载某个应用的对话历史。
     *
     * 首次请求不传 lastCreateTime，会得到最新消息；加载更多时传入当前列表中最早一条
     * 消息的 createTime，服务端就会继续返回更早的数据。
     *
     * @param appId 需要查看的应用 id
     * @param pageSize 本次最多返回的消息数，默认 10 条
     * @param lastCreateTime 上一页最早消息的创建时间，首次加载可不传
     * @param request 当前请求，用于读取 Session 中的登录用户
     * @return 按创建时间倒序排列的历史消息
     */
    @GetMapping("/app/{appId}")
    public BaseResponse<Page<ChatHistory>> listAppChatHistory(
            @PathVariable Long appId,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime lastCreateTime,
            HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        Page<ChatHistory> result = chatHistoryService.listAppChatHistoryByPage(
                appId, pageSize, lastCreateTime, loginUser
        );
        return ResultUtils.success(result);
    }

    /**
     * 管理员分页查看全站对话历史。
     *
     * 请求可以按应用、用户、消息类型和关键字筛选；没有指定排序字段时，服务端默认把
     * 最新消息放在前面，便于查看近期生成活动。
     *
     * @param queryRequest 分页、筛选和排序参数
     * @return 满足条件的对话历史分页数据
     */
    @PostMapping("/admin/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<ChatHistory>> listAllChatHistoryByPageForAdmin(
            @RequestBody ChatHistoryQueryRequest queryRequest) {
        ThrowUtils.throwIf(queryRequest == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(queryRequest.getPageNum() <= 0,
                ErrorCode.PARAMS_ERROR, "页码必须大于 0");
        ThrowUtils.throwIf(queryRequest.getPageSize() <= 0,
                ErrorCode.PARAMS_ERROR, "每页数量必须大于 0");

        QueryWrapper queryWrapper = chatHistoryService.getQueryWrapper(queryRequest);
        Page<ChatHistory> result = chatHistoryService.page(
                Page.of(queryRequest.
              
              getPageNum(), queryRequest.getPageSize()),
                queryWrapper
        );
        return ResultUtils.success(result);
    }
}
