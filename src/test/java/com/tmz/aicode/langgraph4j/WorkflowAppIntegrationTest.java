package com.tmz.aicode.langgraph4j;

import com.tmz.aicode.langgraph4j.state.WorkflowContext;
import com.tmz.aicode.model.enums.CodeGenTypeEnum;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 验证工作流接入应用业务时的参数边界。
 *
 * 这些测试只创建初始状态，不执行任何工作节点，因此不会请求模型、图片服务或构建工程。
 */
class WorkflowAppIntegrationTest {

    /**
     * 工作流必须保留真实应用 id 和既定生成类型，后续节点才能复用普通模式的目录与记忆。
     */
    @Test
    void initialContextKeepsExistingApplicationParameters() {
        WorkflowContext context = WorkflowApp.createInitialContext(
                "继续完善已有项目",
                9001L,
                CodeGenTypeEnum.VUE_PROJECT
        );

        assertEquals(9001L, context.getAppId());
        assertEquals("继续完善已有项目", context.getOriginalPrompt());
        assertEquals(CodeGenTypeEnum.VUE_PROJECT, context.getGenerationType());
        assertEquals("初始化", context.getCurrentStep());
    }

    /** 非法应用 id 不应进入工作流，避免节点写入错误的项目目录。 */
    @Test
    void initialContextRejectsInvalidApplicationId() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> WorkflowApp.createInitialContext(
                        "继续完善已有项目",
                        0L,
                        CodeGenTypeEnum.HTML
                )
        );

        assertEquals("应用 id 必须大于 0", exception.getMessage());
    }

    /** 节点包装器应在业务动作执行前后分别发送一条进度，且不改变状态更新结果。 */
    @Test
    void nodeProgressIsReportedBeforeAndAfterExecution() {
        List<String> progressMessages = new ArrayList<>();
        AsyncNodeAction<MessagesState<String>> node = node_async(
                state -> Map.of("result", "ok"));
        AsyncNodeAction<MessagesState<String>> wrappedNode = WorkflowApp.withProgress(
                "本地测试节点", node, progressMessages::add);

        Map<String, Object> result = wrappedNode
                .apply(new MessagesState<>(Map.of()))
                .join();

        assertEquals("ok", result.get("result"));
        assertEquals(List.of(
                "[AI 工作流] 开始执行：本地测试节点",
                "[AI 工作流] 已完成：本地测试节点"
        ), progressMessages);
    }
}
