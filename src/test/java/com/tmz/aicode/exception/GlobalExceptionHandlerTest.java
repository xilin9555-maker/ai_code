package com.tmz.aicode.exception;

import com.tmz.aicode.common.BaseResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 全局异常处理器的本地单元测试。
 *
 * 测试通过 Spring 的模拟请求和响应验证协议内容，不启动服务器，也不访问外部服务。
 */
class GlobalExceptionHandlerTest {

    /** 测试完成后移除线程请求上下文，防止后续测试被误判为 SSE 请求。 */
    @AfterEach
    void resetRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    /**
     * SSE 控制器执行前产生的限流异常，应写成前端已监听的具名错误事件。
     */
    @Test
    void rateLimitErrorIsWrittenAsSseEvent() throws UnsupportedEncodingException {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET",
                "/api/app/chat/gen/code"
        );
        request.addHeader(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE);
        MockHttpServletResponse response = new MockHttpServletResponse();
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(request, response)
        );

        BaseResponse<?> result = new GlobalExceptionHandler().businessExceptionHandler(
                new BusinessException(
                        ErrorCode.TOO_MANY_REQUEST,
                        "AI 对话请求过于频繁，请稍后再试"
                )
        );

        String responseBody = response.getContentAsString(StandardCharsets.UTF_8);
        assertNull(result);
        assertTrue(response.getContentType().startsWith(MediaType.TEXT_EVENT_STREAM_VALUE));
        assertTrue(responseBody.startsWith("event: generation_error\n"));
        assertTrue(responseBody.contains("\"code\":42900"));
        assertTrue(responseBody.contains("AI 对话请求过于频繁，请稍后再试"));
        assertTrue(responseBody.endsWith("\n\n"));
    }

    /** 普通 HTTP 请求仍保持项目统一的 JSON 业务响应，不受 SSE 分支影响。 */
    @Test
    void normalRequestKeepsBaseResponse() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/app/get/vo");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(request, response)
        );

        BaseResponse<?> result = new GlobalExceptionHandler().businessExceptionHandler(
                new BusinessException(ErrorCode.TOO_MANY_REQUEST)
        );

        assertEquals(ErrorCode.TOO_MANY_REQUEST.getCode(), result.getCode());
        assertEquals(ErrorCode.TOO_MANY_REQUEST.getMessage(), result.getMessage());
        assertEquals(0, response.getContentAsByteArray().length);
    }
}
