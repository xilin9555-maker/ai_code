package com.tmz.aicode.exception;

import cn.hutool.json.JSONUtil;
import com.tmz.aicode.common.BaseResponse;
import com.tmz.aicode.common.ResultUtils;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 将控制器异常转换为统一响应，详细错误仅写入日志。
 */
@Hidden
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 请求体不是合法 JSON，或字段类型无法转换时返回明确的参数错误。
     *
     * 这类异常发生在进入 Controller 方法之前，单独处理可以避免客户端只看到笼统的
     * 系统错误，也便于快速发现命令行引号或请求体格式问题。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public BaseResponse<?> httpMessageNotReadableExceptionHandler(HttpMessageNotReadableException e) {
        log.warn("请求体格式错误", e);
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, "请求体格式错误");
    }

    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> businessExceptionHandler(BusinessException e) {
        log.error("BusinessException", e);
        /*
         * 限流切面在 Controller 方法执行前运行，此时方法内部的 Flux 错误处理尚未建立。
         * SSE 请求需要在这里直接写入具名错误事件，普通接口仍返回统一 JSON 响应。
         */
        if (handleSseError(e.getCode(), e.getMessage())) {
            return null;
        }
        return ResultUtils.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public BaseResponse<?> runtimeExceptionHandler(RuntimeException e) {
        log.error("RuntimeException", e);
        if (handleSseError(ErrorCode.SYSTEM_ERROR.getCode(), "系统错误")) {
            return null;
        }
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误");
    }

    /**
     * 把进入响应流之前发生的异常转换成浏览器可以接收的 SSE 错误事件。
     *
     * Controller 内部的异步错误由 Flux 自己转换；限流、参数解析等异常可能在 Controller
     * 方法执行前产生，只能由全局异常处理器写入响应。事件名称继续使用前端已经监听的
     * generation_error，避免业务错误与浏览器原生连接错误混淆。
     *
     * @param errorCode 需要发送给前端的业务错误码
     * @param errorMessage 面向用户的错误信息
     * @return 当前请求属于 SSE 且已经完成错误写入时返回 true
     */
    private boolean handleSseError(int errorCode, String errorMessage) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return false;
        }

        HttpServletRequest request = servletAttributes.getRequest();
        HttpServletResponse response = servletAttributes.getResponse();
        if (response == null || !isSseRequest(request)) {
            return false;
        }

        try {
            response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform");
            response.setHeader("X-Accel-Buffering", "no");

            Map<String, Object> errorData = Map.of(
                    "error", true,
                    "code", errorCode,
                    "message", errorMessage
            );
            String errorJson = JSONUtil.toJsonStr(errorData);
            response.getWriter().write(
                    "event: generation_error\ndata: " + errorJson + "\n\n"
            );
            response.getWriter().flush();
            return true;
        } catch (IOException ioException) {
            /*
             * 当前请求已经确定为 SSE，即使客户端提前断开导致写入失败，也不能再尝试返回
             * 普通 JSON，否则同一个响应会被写入两种不兼容的内容类型。
             */
            log.warn("写入 SSE 错误事件失败，uri={}", request.getRequestURI(), ioException);
            return true;
        }
    }

    /**
     * 同时检查 Accept 和固定接口路径，兼容部分代理没有完整转发 Accept 请求头的情况。
     */
    private boolean isSseRequest(HttpServletRequest request) {
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        String requestUri = request.getRequestURI();
        return (accept != null && accept.contains(MediaType.TEXT_EVENT_STREAM_VALUE))
                || (requestUri != null && requestUri.contains("/chat/gen/code"));
    }
}
