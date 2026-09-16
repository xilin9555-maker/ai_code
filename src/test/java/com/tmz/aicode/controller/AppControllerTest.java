package com.tmz.aicode.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONUtil;
import com.tmz.aicode.constant.AppConstant;
import com.tmz.aicode.exception.BusinessException;
import com.tmz.aicode.model.entity.App;
import com.tmz.aicode.model.entity.User;
import com.tmz.aicode.service.AppService;
import com.tmz.aicode.service.ProjectDownloadService;
import com.tmz.aicode.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import reactor.core.publisher.Flux;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

/**
 * 应用 SSE 接口的本地单元测试。
 *
 * 流式内容由模拟服务直接提供，测试不会连接数据库或请求模型。
 */
class AppControllerTest {

    /**
     * 接口必须使用约定的 GET 路径和 SSE 响应类型，浏览器才能通过 EventSource 对接。
     */
    @Test
    void chatEndpointDeclaresSseContract() throws NoSuchMethodException {
        Method method = AppController.class.getMethod(
                "chatToGenCode",
                Long.class,
                String.class,
                boolean.class,
                HttpServletRequest.class,
                HttpServletResponse.class
        );
        GetMapping mapping = method.getAnnotation(GetMapping.class);

        assertArrayEquals(new String[]{"/chat/gen/code"}, mapping.value());
        assertArrayEquals(
                new String[]{MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8"},
                mapping.produces()
        );
    }

    /**
     * Controller 应取得 Session 中的用户，将片段包装到 JSON 的 d 字段，并在最后发出 done。
     * 片段刻意保留了行首空格和换行，用来验证代码格式没有在包装过程中丢失。
     */
    @Test
    void chatToGenCodeWrapsChunksAndAppendsDoneEvent() {
        AppService appService = mock(AppService.class);
        UserService userService = mock(UserService.class);
        AppController controller = new AppController(
                appService,
                userService,
                mock(ProjectDownloadService.class)
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        User loginUser = User.builder().id(1001L).build();
        when(userService.getLoginUser(request)).thenReturn(loginUser);
        when(appService.chatToGenCode(2001L, "生成任务管理网站", loginUser, true))
                .thenReturn(Flux.just("  第一段\n", "第二段"));

        List<ServerSentEvent<String>> events = controller.chatToGenCode(
                        2001L,
                        "生成任务管理网站",
                        true,
                        request,
                        response
                )
                .collectList()
                .block();

        assertEquals(3, events.size());
        assertEquals("  第一段\n", JSONUtil.parseObj(events.get(0).data()).getStr("d"));
        assertEquals("第二段", JSONUtil.parseObj(events.get(1).data()).getStr("d"));
        assertEquals("done", events.get(2).event());
        assertTrue(JSONUtil.parseObj(events.get(2).data()).getBool("completed"));
        assertEquals("UTF-8", response.getCharacterEncoding());
        assertEquals("no-cache, no-transform", response.getHeader(HttpHeaders.CACHE_CONTROL));
        assertEquals("no", response.getHeader("X-Accel-Buffering"));
        verify(userService).getLoginUser(request);
        verify(appService).chatToGenCode(2001L, "生成任务管理网站", loginUser, true);
    }

    /**
     * 通过 Spring MVC 的真实返回值处理链验证 SSE 文本格式，而不只检查 Java 对象。
     * 模拟服务直接发出两个固定片段，因此不会连接数据库或调用大模型。
     */
    @Test
    void chatToGenCodeWritesBrowserCompatibleSseFrames() throws Exception {
        AppService appService = mock(AppService.class);
        UserService userService = mock(UserService.class);
        AppController controller = new AppController(
                appService,
                userService,
                mock(ProjectDownloadService.class)
        );
        User loginUser = User.builder().id(1001L).build();
        when(userService.getLoginUser(org.mockito.ArgumentMatchers.any(HttpServletRequest.class)))
                .thenReturn(loginUser);
        when(appService.chatToGenCode(2001L, "生成任务管理网站", loginUser, false))
                .thenReturn(Flux.just("chunk-1", "chunk-2"));

        MockMvc mockMvc = standaloneSetup(controller).build();
        MvcResult pendingResult = mockMvc
                .perform(get("/app/chat/gen/code")
                        .param("appId", "2001")
                        .param("message", "生成任务管理网站")
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

        assertTrue(body.contains("data:{\"d\":\"chunk-1\"}"), body);
        assertTrue(body.contains("data:{\"d\":\"chunk-2\"}"), body);
        assertTrue(body.contains("event:done"), body);
        assertTrue(body.contains("data:{\"completed\":true}"), body);
    }

    /**
     * 上游失败时不能发送 done，否则前端会把只生成了一部分的代码误判为完整结果。
     */
    @Test
    void chatToGenCodeReturnsNamedErrorEventAfterUpstreamError() {
        AppService appService = mock(AppService.class);
        UserService userService = mock(UserService.class);
        AppController controller = new AppController(
                appService,
                userService,
                mock(ProjectDownloadService.class)
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        User loginUser = User.builder().id(1001L).build();
        when(userService.getLoginUser(request)).thenReturn(loginUser);
        when(appService.chatToGenCode(2001L, "生成任务管理网站", loginUser, false))
                .thenReturn(Flux.concat(
                        Flux.just("已生成片段"),
                        Flux.error(new IllegalStateException("生成中断"))
                ));

        List<ServerSentEvent<String>> receivedEvents = controller.chatToGenCode(
                        2001L, "生成任务管理网站", false, request, response)
                .collectList()
                .block();

        assertEquals(2, receivedEvents.size());
        assertEquals("已生成片段",
                JSONUtil.parseObj(receivedEvents.getFirst().data()).getStr("d"));
        assertEquals("generation_error", receivedEvents.get(1).event());
        assertEquals("生成失败：生成中断",
                JSONUtil.parseObj(receivedEvents.get(1).data()).getStr("message"));
        assertTrue(receivedEvents.stream().noneMatch(
                event -> "done".equals(event.event())));
    }

    /**
     * 空消息应在读取登录状态和调用生成服务之前被拒绝。
     */
    @Test
    void chatToGenCodeRejectsBlankMessageEarly() {
        AppService appService = mock(AppService.class);
        UserService userService = mock(UserService.class);
        AppController controller = new AppController(
                appService,
                userService,
                mock(ProjectDownloadService.class)
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> controller.chatToGenCode(
                        2001L,
                        "   ",
                        false,
                        new MockHttpServletRequest(),
                        new MockHttpServletResponse()
                )
        );

        assertEquals("用户消息不能为空", exception.getMessage());
        verifyNoInteractions(userService, appService);
    }

    /**
     * 创建者下载应用时，控制器应定位原始代码目录并把 ZIP 响应交给下载服务。
     */
    @Test
    void downloadAppCodeUsesOriginalGeneratedDirectory() {
        AppService appService = mock(AppService.class);
        UserService userService = mock(UserService.class);
        ProjectDownloadService downloadService = mock(ProjectDownloadService.class);
        AppController controller = new AppController(appService, userService, downloadService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        long appId = 930001L;
        long userId = 1001L;
        App app = App.builder()
                .id(appId)
                .userId(userId)
                .codeGenType("multi_file")
                .build();
        File sourceDir = new File(AppConstant.CODE_OUTPUT_ROOT_DIR, "multi_file_" + appId);

        try {
            FileUtil.mkdir(sourceDir);
            when(appService.getById(appId)).thenReturn(app);
            when(userService.getLoginUser(request)).thenReturn(User.builder().id(userId).build());

            controller.downloadAppCode(appId, request, response);

            verify(downloadService).downloadProjectAsZip(
                    sourceDir.getAbsolutePath(),
                    String.valueOf(appId),
                    response
            );
        } finally {
            FileUtil.del(sourceDir);
        }
    }

    /**
     * 非创建者不能下载代码，并且权限失败后不应继续访问文件压缩服务。
     */
    @Test
    void downloadAppCodeRejectsAnotherUser() {
        AppService appService = mock(AppService.class);
        UserService userService = mock(UserService.class);
        ProjectDownloadService downloadService = mock(ProjectDownloadService.class);
        AppController controller = new AppController(appService, userService, downloadService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        long appId = 930002L;
        when(appService.getById(appId)).thenReturn(App.builder()
                .id(appId)
                .userId(1001L)
                .codeGenType("multi_file")
                .build());
        when(userService.getLoginUser(request)).thenReturn(User.builder().id(1002L).build());

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> controller.downloadAppCode(
                        appId,
                        request,
                        new MockHttpServletResponse()
                )
        );

        assertEquals("无权限下载该应用代码", exception.getMessage());
        verifyNoInteractions(downloadService);
    }
}
