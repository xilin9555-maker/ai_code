package com.tmz.aicode.langgraph4j;

import com.tmz.aicode.langgraph4j.state.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphRepresentation;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.prebuilt.MessagesStateGraph;

import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 使用 WorkflowContext 传递业务状态的网站生成工作流示例。
 *
 * 当前阶段仍不调用真实的图片、模型和构建服务。每个节点只更新 currentStep，用最小逻辑验证
 * 自定义上下文能够沿工作流完整传递，后续开发可以直接在对应节点中补充业务字段。
 */
@Slf4j
public final class SimpleStatefulWorkflowApp {

    public static final String IMAGE_COLLECTOR = "image_collector";
    public static final String PROMPT_ENHANCER = "prompt_enhancer";
    public static final String ROUTER = "router";
    public static final String CODE_GENERATOR = "code_generator";
    public static final String PROJECT_BUILDER = "project_builder";

    private SimpleStatefulWorkflowApp() {
        // 工作流由静态工厂方法创建，不需要实例化该类。
    }

    /**
     * 创建一个能够读取并更新共享上下文的异步节点。
     *
     * @param nodeName 写入 currentStep 的稳定节点名称
     * @param message  方便开发阶段查看执行过程的日志说明
     * @return 可以注册到 MessagesStateGraph 的异步节点
     */
    static AsyncNodeAction<MessagesState<String>> makeStatefulNode(
            String nodeName, String message) {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            if (context == null) {
                throw new IllegalStateException(
                        "缺少工作流上下文，请使用 WorkflowContext.saveContext 初始化状态");
            }
            log.info("执行工作流节点：{}，原始需求：{}", message, context.getOriginalPrompt());
            context.setCurrentStep(nodeName);
            return WorkflowContext.saveContext(context);
        });
    }

    /**
     * 创建并编译带共享状态的网站生成工作流。
     *
     * @return 已完成节点和边校验的可执行工作流
     * @throws GraphStateException 工作流结构不合法时抛出
     */
    public static CompiledGraph<MessagesState<String>> createWorkflow()
            throws GraphStateException {
        return new MessagesStateGraph<String>()
                .addNode(IMAGE_COLLECTOR,
                        makeStatefulNode(IMAGE_COLLECTOR, "获取图片素材"))
                .addNode(PROMPT_ENHANCER,
                        makeStatefulNode(PROMPT_ENHANCER, "增强提示词"))
                .addNode(ROUTER,
                        makeStatefulNode(ROUTER, "智能路由选择"))
                .addNode(CODE_GENERATOR,
                        makeStatefulNode(CODE_GENERATOR, "网站代码生成"))
                .addNode(PROJECT_BUILDER,
                        makeStatefulNode(PROJECT_BUILDER, "项目构建"))
                .addEdge(START, IMAGE_COLLECTOR)
                .addEdge(IMAGE_COLLECTOR, PROMPT_ENHANCER)
                .addEdge(PROMPT_ENHANCER, ROUTER)
                .addEdge(ROUTER, CODE_GENERATOR)
                .addEdge(CODE_GENERATOR, PROJECT_BUILDER)
                .addEdge(PROJECT_BUILDER, END)
                .compile();
    }

    /**
     * 独立执行带状态工作流，并输出 Mermaid 结构和每一步的上下文。
     *
     * @param args 命令行参数，当前未使用
     * @throws GraphStateException 工作流无法通过编译校验时抛出
     */
    public static void main(String[] args) throws GraphStateException {
        CompiledGraph<MessagesState<String>> workflow = createWorkflow();
        GraphRepresentation graph = workflow.getGraph(GraphRepresentation.Type.MERMAID);
        log.info("带状态的网站生成工作流结构：\n{}", graph.content());

        WorkflowContext initialContext = WorkflowContext.builder()
                .originalPrompt("创建一个个人博客网站")
                .currentStep("初始化")
                .build();
        Map<String, Object> initialState = WorkflowContext.saveContext(initialContext);

        for (NodeOutput<MessagesState<String>> step : workflow.stream(initialState)) {
            WorkflowContext context = WorkflowContext.getContext(step.state());
            log.info("节点 {} 执行完成，当前上下文：{}", step.node(), context);
        }
        log.info("带状态的网站生成工作流执行完成");
    }
}
