package com.tmz.aicode.controller;

import cn.hutool.json.JSONUtil;
import com.tmz.aicode.exception.BusinessException;
import com.tmz.aicode.model.entity.User;
import com.tmz.aicode.service.AppService;
import com.tmz.aicode.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import reactor.core.publisher.Flux;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
                HttpServletRequest.class
        );
        GetMapping mapping = method.getAnnotation(GetMapping.class);

        assertArrayEquals(new String[]{"/chat/gen/code"}, mapping.value());
        assertArrayEquals(new String[]{MediaType.TEXT_EVENT_STREAM_VALUE}, mapping.produces());
    }

    /**
     * Controller 应取得 Session 中的用户，将片段包装到 JSON 的 d 字段，并在最后发出 done。
     * 片段刻意保留了行首空格和换行，用来验证代码格式没有在包装过程中丢失。
     */
    @Test
    void chatToGenCodeWrapsChunksAndAppendsDoneEvent() {
        AppService appService = mock(AppService.class);
        UserService userService = mock(UserService.class);
        AppController controller = new AppController(appService, userService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        User loginUser = User.builder().id(1001L).build();
        when(userService.getLoginUser(request)).thenReturn(loginUser);
        when(appService.chatToGenCode(2001L, "生成任务管理网站", loginUser))
                .thenReturn(Flux.just("  第一段\n", "第二段"));

        List<ServerSentEvent<String>> events = controller.chatToGenCode(
                        2001L,
                        "生成任务管理网站",
                        request
                )
                .collectList()
                .block();

        assertEquals(3, events.size());
        assertEquals("  第一段\n", JSONUtil.parseObj(events.get(0).data()).getStr("d"));
        assertEquals("第二段", JSONUtil.parseObj(events.get(1).data()).getStr("d"));
        assertEquals("done", events.get(2).event());
        assertEquals("", events.get(2).data());
        verify(userService).getLoginUser(request);
        verify(appService).chatToGenCode(2001L, "生成任务管理网站", loginUser);
    }

    /**
     * 上游失败时不能发送 done，否则前端会把只生成了一部分的代码误判为完整结果。
     */
    @Test
    void chatToGenCodeDoesNotSendDoneAfterUpstreamError() {
        AppService appService = mock(AppService.class);
        UserService userService = mock(UserService.class);
        AppController controller = new AppController(appService, userService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        User loginUser = User.builder().id(1001L).build();
        when(userService.getLoginUser(request)).thenReturn(loginUser);
        when(appService.chatToGenCode(2001L, "生成任务管理网站", loginUser))
                .thenReturn(Flux.concat(
                        Flux.just("已生成片段"),
                        Flux.error(new IllegalStateException("生成中断"))
                ));

        List<ServerSentEvent<String>> receivedEvents = new java.util.ArrayList<>();
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> controller.chatToGenCode(2001L, "生成任务管理网站", request)
                        .doOnNext(receivedEvents::add)
                        .then()
                        .block()
        );

        assertEquals("生成中断", exception.getMessage());
        assertEquals(1, receivedEvents.size());
        assertEquals("已生成片段",
                JSONUtil.parseObj(receivedEvents.getFirst().data()).getStr("d"));
    }

    /**
     * 空消息应在读取登录状态和调用生成服务之前被拒绝。
     */
    @Test
    void chatToGenCodeRejectsBlankMessageEarly() {
        AppService appService = mock(AppService.class);
        UserService userService = mock(UserService.class);
        AppController controller = new AppController(appService, userService);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> controller.chatToGenCode(
                        2001L,
                        "   ",
                        new MockHttpServletRequest()
                )
        );

        assertEquals("用户消息不能为空", exception.getMessage());
        verifyNoInteractions(userService, appService);
    }
}
