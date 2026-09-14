package com.tmz.aicode.service.impl;

import cn.hutool.core.io.FileUtil;
import com.tmz.aicode.ai.AiCodeGenTypeRoutingService;
import com.tmz.aicode.constant.AppConstant;
import com.tmz.aicode.core.AiCodeGeneratorFacade;
import com.tmz.aicode.core.builder.VueProjectBuilder;
import com.tmz.aicode.core.handler.JsonMessageStreamHandler;
import com.tmz.aicode.core.handler.StreamHandlerExecutor;
import com.tmz.aicode.exception.BusinessException;
import com.tmz.aicode.model.dto.app.AppAddRequest;
import com.tmz.aicode.model.dto.app.AppQueryRequest;
import com.tmz.aicode.model.entity.App;
import com.tmz.aicode.model.entity.User;
import com.tmz.aicode.model.vo.AppVO;
import com.tmz.aicode.model.vo.UserVO;
import com.tmz.aicode.mq.ScreenshotTaskProducer;
import com.tmz.aicode.service.ChatHistoryService;
import com.tmz.aicode.service.UserService;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 应用服务的本地单元测试。
 *
 * 测试只使用内存对象和模拟的用户服务，不会连接数据库，也不会调用模型服务。
 */
class AppServiceImplTest {

    /**
     * 创建应用时应把整理后的需求交给智能路由，并保存路由选择的生成类型。
     * 路由服务和数据库写入都由本地模拟对象代替，不会请求真实模型。
     */
    @Test
    void createAppUsesAiSelectedCodeGenType() {
        AiCodeGenTypeRoutingService routingService = mock(AiCodeGenTypeRoutingService.class);
        AppServiceImpl appService = spy(new AppServiceImpl(
                mock(UserService.class),
                routingService,
                mock(AiCodeGeneratorFacade.class),
                mock(ChatHistoryService.class),
                createStreamHandlerExecutor(),
                mock(VueProjectBuilder.class),
                mock(ScreenshotTaskProducer.class)
        ));
        User loginUser = User.builder().id(1001L).build();
        String normalizedPrompt = "创建一个带路由和状态管理的商城后台";
        AppAddRequest request = new AppAddRequest();
        request.setInitPrompt("  " + normalizedPrompt + "  ");
        when(routingService.routeCodeGenType(normalizedPrompt))
                .thenReturn(com.tmz.aicode.model.enums.CodeGenTypeEnum.VUE_PROJECT);
        doAnswer(invocation -> {
            App savedApp = invocation.getArgument(0);
            savedApp.setId(4001L);
            return true;
        }).when(appService).save(any(App.class));

        Long appId = appService.createApp(request, loginUser);

        assertEquals(4001L, appId);
        verify(routingService).routeCodeGenType(normalizedPrompt);
        verify(appService).save(argThat(savedApp ->
                normalizedPrompt.equals(savedApp.getInitPrompt())
                        && normalizedPrompt.substring(0, 12).equals(savedApp.getAppName())
                        && "vue_project".equals(savedApp.getCodeGenType())
                        && Integer.valueOf(AppConstant.DEFAULT_APP_PRIORITY)
                        .equals(savedApp.getPriority())
                        && loginUser.getId().equals(savedApp.getUserId())
        ));
    }

    /**
     * 重复部署已有 deployKey 的应用时应覆盖静态文件并保持访问地址不变。
     */
    @Test
    void deployAppCopiesFilesAndKeepsExistingUrl() {
        UserService userService = mock(UserService.class);
        VueProjectBuilder vueProjectBuilder = mock(VueProjectBuilder.class);
        AppServiceImpl appService = spy(new AppServiceImpl(
                userService,
                mock(AiCodeGenTypeRoutingService.class),
                mock(AiCodeGeneratorFacade.class),
                mock(ChatHistoryService.class),
                createStreamHandlerExecutor(),
                vueProjectBuilder,
                mock(ScreenshotTaskProducer.class)
        ));
        long appId = 920001L;
        String deployKey = "aB3xY9";
        User loginUser = User.builder().id(1001L).build();
        App app = App.builder()
                .id(appId)
                .userId(loginUser.getId())
                .codeGenType("multi_file")
                .deployKey(deployKey)
                .build();
        File sourceDir = new File(
                AppConstant.CODE_OUTPUT_ROOT_DIR,
                "multi_file_" + appId
        );
        File deployDir = new File(AppConstant.CODE_DEPLOY_ROOT_DIR, deployKey);

        try {
            FileUtil.mkdir(sourceDir);
            FileUtil.writeString(
                    "<h1>部署测试</h1>",
                    new File(sourceDir, "index.html"),
                    StandardCharsets.UTF_8
            );
            doReturn(app).when(appService).getById(appId);
            doReturn(true).when(appService).updateById(argThat(update ->
                    update != null
                            && Long.valueOf(appId).equals(update.getId())
                            && deployKey.equals(update.getDeployKey())
                            && update.getDeployedTime() != null
            ));
            doNothing().when(appService).generateAppScreenshotAsync(
                    appId, "http://localhost/" + deployKey + "/");

            String deployUrl = appService.deployApp(appId, loginUser);

            assertEquals("http://localhost/" + deployKey + "/", deployUrl);
            assertEquals(
                    "<h1>部署测试</h1>",
                    FileUtil.readString(new File(deployDir, "index.html"), StandardCharsets.UTF_8)
            );
            verifyNoInteractions(vueProjectBuilder);
            verify(appService).generateAppScreenshotAsync(appId, deployUrl);
        } finally {
            FileUtil.del(sourceDir);
            FileUtil.del(deployDir);
        }
    }

    /**
     * Vue 工程必须先完成构建，并且只把 dist 中的浏览器成品复制到部署目录。
     *
     * 构建器在测试中由固定结果代替，因此不会执行 npm，也不会访问网络。
     */
    @Test
    void deployVueProjectBuildsAndCopiesDistDirectory() {
        VueProjectBuilder vueProjectBuilder = mock(VueProjectBuilder.class);
        AppServiceImpl appService = spy(new AppServiceImpl(
                mock(UserService.class),
                mock(AiCodeGenTypeRoutingService.class),
                mock(AiCodeGeneratorFacade.class),
                mock(ChatHistoryService.class),
                createStreamHandlerExecutor(),
                vueProjectBuilder,
                mock(ScreenshotTaskProducer.class)
        ));
        long appId = 920002L;
        String deployKey = "vUe123";
        User loginUser = User.builder().id(1002L).build();
        App app = App.builder()
                .id(appId)
                .userId(loginUser.getId())
                .codeGenType("vue_project")
                .deployKey(deployKey)
                .build();
        File sourceDir = new File(AppConstant.CODE_OUTPUT_ROOT_DIR, "vue_project_" + appId);
        File distDir = new File(sourceDir, "dist");
        File deployDir = new File(AppConstant.CODE_DEPLOY_ROOT_DIR, deployKey);

        try {
            FileUtil.mkdir(distDir);
            FileUtil.writeString(
                    "<h1>Vue 构建成品</h1>",
                    new File(distDir, "index.html"),
                    StandardCharsets.UTF_8
            );
            FileUtil.writeString(
                    "{\"name\":\"source-only\"}",
                    new File(sourceDir, "package.json"),
                    StandardCharsets.UTF_8
            );
            when(vueProjectBuilder.buildProject(sourceDir.getAbsolutePath())).thenReturn(true);
            doReturn(app).when(appService).getById(appId);
            doReturn(true).when(appService).updateById(argThat(update ->
                    update != null
                            && Long.valueOf(appId).equals(update.getId())
                            && deployKey.equals(update.getDeployKey())
                            && update.getDeployedTime() != null
            ));
            doNothing().when(appService).generateAppScreenshotAsync(
                    appId, "http://localhost/" + deployKey + "/");

            String deployUrl = appService.deployApp(appId, loginUser);

            assertEquals("http://localhost/" + deployKey + "/", deployUrl);
            verify(vueProjectBuilder).buildProject(sourceDir.getAbsolutePath());
            assertEquals(
                    "<h1>Vue 构建成品</h1>",
                    FileUtil.readString(new File(deployDir, "index.html"), StandardCharsets.UTF_8)
            );
            assertFalse(new File(deployDir, "package.json").exists());
            verify(appService).generateAppScreenshotAsync(appId, deployUrl);
        } finally {
            FileUtil.del(sourceDir);
            FileUtil.del(deployDir);
        }
    }

    /**
     * 一页中同一用户创建的多个应用应只触发一次批量用户查询。
     */
    @Test
    void getAppVOListLoadsUsersInOneBatch() {
        UserService userService = mock(UserService.class);
        AppServiceImpl appService = new AppServiceImpl(
                userService,
                mock(AiCodeGenTypeRoutingService.class),
                mock(AiCodeGeneratorFacade.class),
                mock(ChatHistoryService.class),
                createStreamHandlerExecutor(),
                mock(VueProjectBuilder.class),
                mock(ScreenshotTaskProducer.class)
        );
        User user = User.builder().id(1001L).userName("创建者").build();
        UserVO userVO = new UserVO();
        userVO.setId(user.getId());
        userVO.setUserName(user.getUserName());
        when(userService.listByIds(anyCollection())).thenReturn(List.of(user));
        when(userService.getUserVO(user)).thenReturn(userVO);

        List<App> apps = List.of(
                App.builder().id(1L).appName("作品一").userId(user.getId()).build(),
                App.builder().id(2L).appName("作品二").userId(user.getId()).build()
        );
        List<AppVO> result = appService.getAppVOList(apps);

        assertEquals(2, result.size());
        assertEquals("作品一", result.get(0).getAppName());
        assertSame(userVO, result.get(0).getUser());
        assertSame(userVO, result.get(1).getUser());
        verify(userService).listByIds(anyCollection());
        verify(userService, never()).getById(user.getId());
    }

    /**
     * 非白名单排序字段不能进入查询条件，避免客户端把任意内容拼进 SQL。
     */
    @Test
    void getQueryWrapperRejectsUnknownSortField() {
        AppServiceImpl appService = new AppServiceImpl(
                mock(UserService.class),
                mock(AiCodeGenTypeRoutingService.class),
                mock(AiCodeGeneratorFacade.class),
                mock(ChatHistoryService.class),
                createStreamHandlerExecutor(),
                mock(VueProjectBuilder.class),
                mock(ScreenshotTaskProducer.class)
        );
        AppQueryRequest request = new AppQueryRequest();
        request.setSortField("unknownColumn");
        request.setSortOrder("ascend");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> appService.getQueryWrapper(request)
        );

        assertEquals("排序字段不合法", exception.getMessage());
    }

    /**
     * 通过所有权校验后，应用服务应把正确的生成类型、消息和 appId 交给门面。
     */
    @Test
    void chatToGenCodeDelegatesAuthorizedAppToFacade() {
        UserService userService = mock(UserService.class);
        AiCodeGeneratorFacade facade = mock(AiCodeGeneratorFacade.class);
        ChatHistoryService chatHistoryService = mock(ChatHistoryService.class);
        AppServiceImpl appService = spy(new AppServiceImpl(
                userService,
                mock(AiCodeGenTypeRoutingService.class),
                facade,
                chatHistoryService,
                createStreamHandlerExecutor(),
                mock(VueProjectBuilder.class),
                mock(ScreenshotTaskProducer.class)
        ));
        long appId = 2001L;
        User loginUser = User.builder().id(1001L).build();
        App app = App.builder()
                .id(appId)
                .userId(loginUser.getId())
                .codeGenType("multi_file")
                .build();
        doReturn(app).when(appService).getById(appId);
        when(facade.generateAndSaveCodeStream(
                "生成任务管理网站",
                com.tmz.aicode.model.enums.CodeGenTypeEnum.MULTI_FILE,
                appId
        )).thenReturn(Flux.just("第一段", "第二段"));
        when(chatHistoryService.addChatMessage(
                org.mockito.ArgumentMatchers.eq(appId),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(loginUser.getId())
        )).thenReturn(true);

        List<String> chunks = appService.chatToGenCode(
                        appId,
                        " 生成任务管理网站 ",
                        loginUser
                )
                .collectList()
                .block();

        assertEquals(List.of("第一段", "第二段"), chunks);
        verify(facade).generateAndSaveCodeStream(
                "生成任务管理网站",
                com.tmz.aicode.model.enums.CodeGenTypeEnum.MULTI_FILE,
                appId
        );
        verify(chatHistoryService).addChatMessage(
                appId,
                "生成任务管理网站",
                "user",
                loginUser.getId()
        );
        verify(chatHistoryService).addChatMessage(
                appId,
                "第一段第二段",
                "ai",
                loginUser.getId()
        );
    }

    /**
     * 当前用户不是应用创建者时，应在调用生成门面前结束流程。
     */
    @Test
    void chatToGenCodeRejectsAnotherUsersApp() {
        AiCodeGeneratorFacade facade = mock(AiCodeGeneratorFacade.class);
        AppServiceImpl appService = spy(new AppServiceImpl(
                mock(UserService.class),
                mock(AiCodeGenTypeRoutingService.class),
                facade,
                mock(ChatHistoryService.class),
                createStreamHandlerExecutor(),
                mock(VueProjectBuilder.class),
                mock(ScreenshotTaskProducer.class)
        ));
        long appId = 2002L;
        App app = App.builder()
                .id(appId)
                .userId(1001L)
                .codeGenType("multi_file")
                .build();
        doReturn(app).when(appService).getById(appId);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> appService.chatToGenCode(
                        appId,
                        "生成作品展示网站",
                        User.builder().id(1002L).build()
                )
        );

        assertEquals("只能为自己创建的应用生成代码", exception.getMessage());
        verifyNoInteractions(facade);
    }

    /**
     * 模拟生成流失败时，应保留用户输入并追加一条 AI 失败记录。
     *
     * 门面直接返回固定异常流，整个测试不会连接真实模型。
     */
    @Test
    void chatToGenCodeSavesFailureMessageWithoutCallingRealModel() {
        AiCodeGeneratorFacade facade = mock(AiCodeGeneratorFacade.class);
        ChatHistoryService chatHistoryService = mock(ChatHistoryService.class);
        AppServiceImpl appService = spy(new AppServiceImpl(
                mock(UserService.class),
                mock(AiCodeGenTypeRoutingService.class),
                facade,
                chatHistoryService,
                createStreamHandlerExecutor(),
                mock(VueProjectBuilder.class),
                mock(ScreenshotTaskProducer.class)
        ));
        long appId = 2003L;
        long userId = 1003L;
        User loginUser = User.builder().id(userId).build();
        App app = App.builder()
                .id(appId)
                .userId(userId)
                .codeGenType("multi_file")
                .build();
        doReturn(app).when(appService).getById(appId);
        when(chatHistoryService.addChatMessage(
                org.mockito.ArgumentMatchers.eq(appId),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(userId)
        )).thenReturn(true);
        when(facade.generateAndSaveCodeStream(
                "生成失败示例",
                com.tmz.aicode.model.enums.CodeGenTypeEnum.MULTI_FILE,
                appId
        )).thenReturn(Flux.error(new IllegalStateException("连接超时")));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> appService.chatToGenCode(appId, "生成失败示例", loginUser)
                        .then()
                        .block()
        );

        assertEquals("连接超时", exception.getMessage());
        verify(chatHistoryService).addChatMessage(
                appId,
                "生成失败示例",
                "user",
                userId
        );
        verify(chatHistoryService).addChatMessage(
                appId,
                "AI 回复失败：连接超时",
                "ai",
                userId
        );
    }

    /**
     * 截图任务应把应用 id 和部署地址完整交给消息生产者。
     *
     * 生产者由模拟对象代替，因此测试不会连接 RabbitMQ，也不会启动浏览器或访问 COS。
     */
    @Test
    void generateAppScreenshotAsyncSendsMessage() {
        ScreenshotTaskProducer screenshotTaskProducer = mock(ScreenshotTaskProducer.class);
        AppServiceImpl appService = new AppServiceImpl(
                mock(UserService.class),
                mock(AiCodeGenTypeRoutingService.class),
                mock(AiCodeGeneratorFacade.class),
                mock(ChatHistoryService.class),
                createStreamHandlerExecutor(),
                mock(VueProjectBuilder.class),
                screenshotTaskProducer
        );
        long appId = 920003L;
        String appUrl = "http://localhost/aB3xY9/";

        appService.generateAppScreenshotAsync(appId, appUrl);

        verify(screenshotTaskProducer).sendScreenshotTask(appId, appUrl);
    }

    /**
     * 使用真实的流处理选择逻辑，测试数据仍全部来自本地固定 Flux。
     */
    private static StreamHandlerExecutor createStreamHandlerExecutor() {
        return new StreamHandlerExecutor(
                new JsonMessageStreamHandler(mock(VueProjectBuilder.class))
        );
    }
}
