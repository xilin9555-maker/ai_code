package com.tmz.aicode.controller;

import cn.hutool.core.util.StrUtil;
import com.tmz.aicode.exception.ErrorCode;
import com.tmz.aicode.exception.ThrowUtils;
import com.tmz.aicode.langgraph4j.WorkflowApp;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;

/**
 * 网站生成工作流的 SSE 接口。
 *
 * 控制器只负责校验输入并声明流式响应协议，工作流创建、子图调度和事件内容都由
 * WorkflowApp 统一处理。
 */
@RestController
@RequestMapping("/workflow")
@Slf4j
public class WorkflowSseController {

    /**
     * 通过 Flux 持续输出工作流的执行进度。
     *
     * 浏览器可以分别监听 workflow_start、step_completed、workflow_completed 和
     * workflow_error 事件。关闭代理缓冲后，每个节点完成事件都能尽快到达客户端。
     *
     * @param prompt 用户提交的网站生成需求
     * @param response 当前 HTTP 响应，用于设置流式传输相关响应头
     * @return 已按 SSE 协议格式化的工作流事件流
     */
    @GetMapping(value = "/execute-flux",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public Flux<ServerSentEvent<String>> executeWorkflowWithFlux(
            @RequestParam String prompt,
            HttpServletResponse response) {
        ThrowUtils.throwIf(StrUtil.isBlank(prompt),
                ErrorCode.PARAMS_ERROR, "网站生成需求不能为空");

        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform");
        response.setHeader("X-Accel-Buffering", "no");

        String normalizedPrompt = StrUtil.trim(prompt);
        log.info("收到 Flux 工作流执行请求：{}", normalizedPrompt);
        return WorkflowApp.executeWorkflowWithFlux(normalizedPrompt);
    }
}
