package com.tmz.aicode.controller;

import com.tmz.aicode.exception.BusinessException;
import com.tmz.aicode.langgraph4j.WorkflowApp;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import reactor.core.publisher.Flux;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/**
 * 工作流 SSE 接口的本地单元测试。
 *
 * 测试使用固定的模拟 Flux，不会创建真实工作流，也不会访问模型、图片服务或对象存储。
 */
class WorkflowSseControllerTest {

    /**
     * 接口路径、请求方式和响应类型需要与 EventSource 的连接约定保持一致。
     */
    @Test
    void executeFluxEndpointDeclaresSseContract() throws NoSuchMethodException {
        Method method = WorkflowSseController.class.getMethod(
                "executeWorkflowWithFlux",
                String.class,
                HttpServletResponse.class
        );
        GetMapping mapping = method.getAnnotation(GetMapping.class);

        assertArrayEquals(new String[]{"/execute-flux"}, mapping.value());
        assertArrayEquals(
                new String[]{MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8"},
                mapping.produces()
        );
    }

    /**
     * 模拟工作流返回两条具名事件，验证响应链不会丢失 event、data 或帧结束空行。
     */
    @Test
    void executeFluxWritesNamedSseFramesWithoutCallingWorkflow() throws Exception {
        ServerSentEvent<String> startEvent = ServerSentEvent.<String>builder()
                .event("workflow_start")
                .data("{\"message\":\"started\"}")
                .build();
        ServerSentEvent<String> completedEvent = ServerSentEvent.<String>builder()
                .event("workflow_completed")
                .data("{\"message\":\"completed\"}")
                .build();

        try (MockedStatic<WorkflowApp> workflowMock = Mockito.mockStatic(WorkflowApp.class)) {
            workflowMock.when(() -> WorkflowApp.executeWorkflowWithFlux("创建个人主页"))
                    .thenReturn(Flux.just(startEvent, completedEvent));

            MockMvc mockMvc = standaloneSetup(new WorkflowSseController()).build();
            MvcResult pendingResult = mockMvc
                    .perform(get("/workflow/execute-flux")
                            .param("prompt", "  创建个人主页  ")
                            .accept(MediaType.TEXT_EVENT_STREAM))
                    .andExpect(request().asyncStarted())
                    .andReturn();

            String body = mockMvc
                    .perform(asyncDispatch(pendingResult))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                    .andReturn()
                    .getResponse()
                    .getContentAsString(StandardCharsets.UTF_8);

            assertTrue(body.contains("event:workflow_start"), body);
            assertTrue(body.contains("data:{\"message\":\"started\"}"), body);
            assertTrue(body.contains("event:workflow_completed"), body);
            assertTrue(body.contains("data:{\"message\":\"completed\"}"), body);
            workflowMock.verify(
                    () -> WorkflowApp.executeWorkflowWithFlux("创建个人主页"));
        }
    }

    /**
     * 控制器应在启动工作流前拒绝空需求，并为正常连接设置禁止缓冲的响应头。
     */
    @Test
    void executeFluxValidatesPromptAndConfiguresStreamingHeaders() {
        WorkflowSseController controller = new WorkflowSseController();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThrows(
                BusinessException.class,
                () -> controller.executeWorkflowWithFlux("  ", response)
        );

        try (MockedStatic<WorkflowApp> workflowMock = Mockito.mockStatic(WorkflowApp.class)) {
            workflowMock.when(() -> WorkflowApp.executeWorkflowWithFlux("创建展示网站"))
                    .thenReturn(Flux.empty());

            controller.executeWorkflowWithFlux("创建展示网站", response);

            assertEquals(StandardCharsets.UTF_8.name(), response.getCharacterEncoding());
            assertEquals("no-cache, no-transform", response.getHeader(HttpHeaders.CACHE_CONTROL));
            assertEquals("no", response.getHeader("X-Accel-Buffering"));
        }
    }
}
