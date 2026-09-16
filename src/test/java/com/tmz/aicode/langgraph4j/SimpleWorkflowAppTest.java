package com.tmz.aicode.langgraph4j;

import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphRepresentation;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证简化工作流的节点顺序、状态追加和 Mermaid 图结构。
 *
 * 测试只执行本地模拟节点，不会调用模型、图片服务、数据库或项目构建命令。
 */
class SimpleWorkflowAppTest {

    /** 五个节点应按既定顺序依次更新 MessagesState。 */
    @Test
    void executesNodesInWebsiteGenerationOrder() throws GraphStateException {
        CompiledGraph<MessagesState<String>> workflow = SimpleWorkflowApp.createWorkflow();

        MessagesState<String> finalState = workflow.invoke(Map.<String, Object>of())
                .orElseThrow();

        assertEquals(List.of(
                "获取图片素材",
                "增强提示词",
                "智能路由选择",
                "网站代码生成",
                "项目构建"
        ), finalState.messages());
    }

    /** Mermaid 文本应包含全部节点以及从开始到结束的完整路径。 */
    @Test
    void exposesCompleteMermaidGraph() throws GraphStateException {
        CompiledGraph<MessagesState<String>> workflow = SimpleWorkflowApp.createWorkflow();

        String mermaid = workflow.getGraph(GraphRepresentation.Type.MERMAID).content();

        assertTrue(mermaid.contains(SimpleWorkflowApp.IMAGE_COLLECTOR));
        assertTrue(mermaid.contains(SimpleWorkflowApp.PROMPT_ENHANCER));
        assertTrue(mermaid.contains(SimpleWorkflowApp.ROUTER));
        assertTrue(mermaid.contains(SimpleWorkflowApp.CODE_GENERATOR));
        assertTrue(mermaid.contains(SimpleWorkflowApp.PROJECT_BUILDER));
        assertTrue(mermaid.contains("__START__"));
        assertTrue(mermaid.contains("__END__"));
    }
}
