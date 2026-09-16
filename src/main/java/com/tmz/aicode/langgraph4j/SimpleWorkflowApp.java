package com.tmz.aicode.langgraph4j;

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
 * 网站生成智能体的简化工作流结构。
 *
 * 当前阶段只定义节点及其先后关系，不调用真实的图片服务、模型或项目构建器。每个节点
 * 仅向 MessagesState 追加一条说明，先验证工作流能够编译、可视化并按预期顺序执行。
 */
@Slf4j
public final class SimpleWorkflowApp {

    public static final String IMAGE_COLLECTOR = "image_collector";
    public static final String PROMPT_ENHANCER = "prompt_enhancer";
    public static final String ROUTER = "router";
    public static final String CODE_GENERATOR = "code_generator";
    public static final String PROJECT_BUILDER = "project_builder";

    private SimpleWorkflowApp() {
        // 工作流通过静态工厂创建，不需要保存对象级状态。
    }

    /**
     * 创建一个只模拟状态流转的异步工作节点。
     *
     * node_async 会把同步节点包装成 CompletableFuture 形式，使当前结构以后可以直接
     * 替换为真正的异步图片搜索或模型调用，而不需要改动工作流图的节点声明。
     *
     * @param message 节点执行时记录到日志和消息状态中的说明
     * @return 可以添加到 MessagesStateGraph 的异步节点
     */
    static AsyncNodeAction<MessagesState<String>> makeNode(String message) {
        return node_async(state -> {
            log.info("执行工作流节点：{}", message);
            return Map.of("messages", message);
        });
    }

    /**
     * 按网站生成流程创建并编译工作流。
     *
     * 流程固定为图片素材收集、提示词增强、生成模式路由、网站代码生成和项目构建。
     * 编译会校验节点和边是否完整，并返回不可变的可执行工作流。
     *
     * @return 已完成结构校验的工作流
     * @throws GraphStateException 节点重复、边指向未知节点或图结构不完整时抛出
     */
    public static CompiledGraph<MessagesState<String>> createWorkflow()
            throws GraphStateException {
        return new MessagesStateGraph<String>()
                .addNode(IMAGE_COLLECTOR, makeNode("获取图片素材"))
                .addNode(PROMPT_ENHANCER, makeNode("增强提示词"))
                .addNode(ROUTER, makeNode("智能路由选择"))
                .addNode(CODE_GENERATOR, makeNode("网站代码生成"))
                .addNode(PROJECT_BUILDER, makeNode("项目构建"))
                .addEdge(START, IMAGE_COLLECTOR)
                .addEdge(IMAGE_COLLECTOR, PROMPT_ENHANCER)
                .addEdge(PROMPT_ENHANCER, ROUTER)
                .addEdge(ROUTER, CODE_GENERATOR)
                .addEdge(CODE_GENERATOR, PROJECT_BUILDER)
                .addEdge(PROJECT_BUILDER, END)
                .compile();
    }

    /**
     * 独立运行简化工作流，并输出 Mermaid 图和每一步状态，方便当前阶段手工验证。
     *
     * @param args 命令行参数，当前未使用
     * @throws GraphStateException 工作流图无法通过编译校验时抛出
     */
    public static void main(String[] args) throws GraphStateException {
        CompiledGraph<MessagesState<String>> workflow = createWorkflow();
        GraphRepresentation graph = workflow.getGraph(GraphRepresentation.Type.MERMAID);
        log.info("网站生成工作流结构：\n{}", graph.content());

        int stepCounter = 1;
        for (NodeOutput<MessagesState<String>> step
                : workflow.stream(Map.<String, Object>of())) {
            log.info("工作流第 {} 步完成，节点：{}，状态：{}",
                    stepCounter++, step.node(), step.state().data());
        }
        log.info("网站生成工作流执行完成");
    }
}
