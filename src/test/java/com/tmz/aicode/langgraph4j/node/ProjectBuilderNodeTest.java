package com.tmz.aicode.langgraph4j.node;

import com.tmz.aicode.core.builder.VueProjectBuilder;
import com.tmz.aicode.exception.BusinessException;
import com.tmz.aicode.langgraph4j.state.WorkflowContext;
import com.tmz.aicode.utils.SpringContextUtil;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.io.File;
import java.util.Map;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

/**
 * 验证工作流项目构建节点的完成与失败语义，测试不会执行真实 npm 命令。
 */
class ProjectBuilderNodeTest {

    @Test
    void successfulBuildStoresDistDirectory() {
        String projectDirectory = "build/vue_project_1001";
        VueProjectBuilder projectBuilder = prepareProjectBuilder(true);
        WorkflowContext context = WorkflowContext.builder()
                .generatedCodeDir(projectDirectory)
                .build();

        Map<String, Object> result = ProjectBuilderNode.create()
                .apply(new MessagesState<>(WorkflowContext.saveContext(context)))
                .join();
        WorkflowContext resultContext = WorkflowContext.getContext(
                new MessagesState<>(result)
        );

        assertEquals(ProjectBuilderNode.STEP_NAME, resultContext.getCurrentStep());
        assertEquals(
                projectDirectory + File.separator + "dist",
                resultContext.getBuildResultDir()
        );
        verify(projectBuilder).buildProject(
                org.mockito.ArgumentMatchers.eq(projectDirectory), any());
    }

    @Test
    void failedBuildStopsWorkflow() {
        String projectDirectory = "build/vue_project_1002";
        prepareProjectBuilder(false);
        WorkflowContext context = WorkflowContext.builder()
                .generatedCodeDir(projectDirectory)
                .build();

        CompletionException exception = assertThrows(
                CompletionException.class,
                () -> ProjectBuilderNode.create()
                        .apply(new MessagesState<>(WorkflowContext.saveContext(context)))
                        .join()
        );

        BusinessException businessException =
                assertInstanceOf(BusinessException.class, exception.getCause());
        assertEquals(
                "Vue 项目构建失败，请检查生成代码和依赖",
                businessException.getMessage()
        );
    }

    /**
     * 为静态工作流节点准备只包含模拟构建器的 Spring 上下文。
     */
    private VueProjectBuilder prepareProjectBuilder(boolean buildSuccess) {
        VueProjectBuilder projectBuilder = mock(VueProjectBuilder.class);
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        when(applicationContext.getBean(VueProjectBuilder.class)).thenReturn(projectBuilder);
        when(projectBuilder.buildProject(
                org.mockito.ArgumentMatchers.anyString(), any()))
                .thenReturn(buildSuccess);
        new SpringContextUtil().setApplicationContext(applicationContext);
        return projectBuilder;
    }
}
