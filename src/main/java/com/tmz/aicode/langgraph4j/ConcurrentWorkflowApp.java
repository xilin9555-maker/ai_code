package com.tmz.aicode.langgraph4j;

import cn.hutool.core.thread.ExecutorBuilder;
import cn.hutool.core.thread.ThreadFactoryBuilder;
import com.tmz.aicode.exception.BusinessException;
import com.tmz.aicode.exception.ErrorCode;
import com.tmz.aicode.langgraph4j.model.QualityResult;
import com.tmz.aicode.langgraph4j.node.CodeGeneratorNode;
import com.tmz.aicode.langgraph4j.node.CodeQualityCheckNode;
import com.tmz.aicode.langgraph4j.node.ProjectBuilderNode;
import com.tmz.aicode.langgraph4j.node.PromptEnhancerNode;
import com.tmz.aicode.langgraph4j.node.RouterNode;
import com.tmz.aicode.langgraph4j.node.concurrent.ContentImageCollectorNode;
import com.tmz.aicode.langgraph4j.node.concurrent.DiagramCollectorNode;
import com.tmz.aicode.langgraph4j.node.concurrent.IllustrationCollectorNode;
import com.tmz.aicode.langgraph4j.node.concurrent.ImageAggregatorNode;
import com.tmz.aicode.langgraph4j.node.concurrent.ImagePlanNode;
import com.tmz.aicode.langgraph4j.node.concurrent.LogoCollectorNode;
import com.tmz.aicode.langgraph4j.state.WorkflowContext;
import com.tmz.aicode.model.enums.CodeGenTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphRepresentation;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.prebuilt.MessagesStateGraph;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;

/**
 * 使用 LangGraph4j Parallel Branch 收集图片的代码生成工作流。
 *
 * 图片规划完成后，内容图片、插画、架构图和 Logo 四个分支由专用线程池并发执行；框架
 * 等待全部分支结束后调用聚合节点，再继续提示词增强、路由、生成、质检和构建流程。
 */
@Slf4j
public class ConcurrentWorkflowApp {

    public static final String IMAGE_PLAN = "image_plan";
    public static final String CONTENT_IMAGE_COLLECTOR = "content_image_collector";
    public static final String ILLUSTRATION_COLLECTOR = "illustration_collector";
    public static final String DIAGRAM_COLLECTOR = "diagram_collector";
    public static final String LOGO_COLLECTOR = "logo_collector";
    public static final String IMAGE_AGGREGATOR = "image_aggregator";
    public static final String PROMPT_ENHANCER = "prompt_enhancer";
    public static final String ROUTER = "router";
    public static final String CODE_GENERATOR = "code_generator";
    public static final String CODE_QUALITY_CHECK = "code_quality_check";
    public static final String PROJECT_BUILDER = "project_builder";

    /** 质检通过并且 Vue 工程需要构建时使用的路由标识。 */
    private static final String BUILD_ROUTE = "build";

    /** 质检通过但原生网站无需构建时使用的路由标识。 */
    private static final String SKIP_BUILD_ROUTE = "skip_build";

    /** 质检未通过、需要回到代码生成节点修复时使用的路由标识。 */
    private static final String QUALITY_FAIL_ROUTE = "fail";

    /**
     * 创建并编译带有四条图片并发分支的工作流。
     *
     * @return 可以配合 RunnableConfig 执行的工作流
     */
    public CompiledGraph<MessagesState<String>> createWorkflow() {
        try {
            return new MessagesStateGraph<String>()
                    // 常规串行节点。
                    .addNode(IMAGE_PLAN, ImagePlanNode.create())
                    .addNode(PROMPT_ENHANCER, PromptEnhancerNode.create())
                    .addNode(ROUTER, RouterNode.create())
                    .addNode(CODE_GENERATOR, CodeGeneratorNode.create())
                    .addNode(CODE_QUALITY_CHECK, CodeQualityCheckNode.create())
                    .addNode(PROJECT_BUILDER, ProjectBuilderNode.create())

                    // 四个图片收集分支以及统一的结果聚合节点。
                    .addNode(CONTENT_IMAGE_COLLECTOR, ContentImageCollectorNode.create())
                    .addNode(ILLUSTRATION_COLLECTOR, IllustrationCollectorNode.create())
                    .addNode(DIAGRAM_COLLECTOR, DiagramCollectorNode.create())
                    .addNode(LOGO_COLLECTOR, LogoCollectorNode.create())
                    .addNode(IMAGE_AGGREGATOR, ImageAggregatorNode.create())

                    .addEdge(START, IMAGE_PLAN)

                    // 从同一规划节点连接到多个节点，形成可并行调度的图片收集分支。
                    .addEdge(IMAGE_PLAN, CONTENT_IMAGE_COLLECTOR)
                    .addEdge(IMAGE_PLAN, ILLUSTRATION_COLLECTOR)
                    .addEdge(IMAGE_PLAN, DIAGRAM_COLLECTOR)
                    .addEdge(IMAGE_PLAN, LOGO_COLLECTOR)

                    // 四个分支都以聚合节点为终点，框架会等待所有分支完成后再继续。
                    .addEdge(CONTENT_IMAGE_COLLECTOR, IMAGE_AGGREGATOR)
                    .addEdge(ILLUSTRATION_COLLECTOR, IMAGE_AGGREGATOR)
                    .addEdge(DIAGRAM_COLLECTOR, IMAGE_AGGREGATOR)
                    .addEdge(LOGO_COLLECTOR, IMAGE_AGGREGATOR)

                    // 图片聚合完成后恢复常规的串行生成流程。
                    .addEdge(IMAGE_AGGREGATOR, PROMPT_ENHANCER)
                    .addEdge(PROMPT_ENHANCER, ROUTER)
                    .addEdge(ROUTER, CODE_GENERATOR)
                    .addEdge(CODE_GENERATOR, CODE_QUALITY_CHECK)

                    // 质检失败时循环修复，通过后根据生成类型构建或直接结束。
                    .addConditionalEdges(
                            CODE_QUALITY_CHECK,
                            edge_async(this::routeAfterQualityCheck),
                            Map.of(
                                    BUILD_ROUTE, PROJECT_BUILDER,
                                    SKIP_BUILD_ROUTE, END,
                                    QUALITY_FAIL_ROUTE, CODE_GENERATOR
                            )
                    )
                    .addEdge(PROJECT_BUILDER, END)
                    .compile();
        } catch (GraphStateException exception) {
            throw new BusinessException(
                    ErrorCode.OPERATION_ERROR, "并发工作流创建失败");
        }
    }

    /**
     * 执行并发图片收集工作流并返回最终上下文。
     *
     * RunnableConfig 将专用线程池绑定到图片计划节点的并行分支。线程池只服务于当前一次
     * 执行，并在流程结束后关闭，避免重复运行时遗留非守护线程。
     *
     * @param originalPrompt 用户最初提交的网站生成需求
     * @return 工作流最后一个节点产生的上下文
     */
    public WorkflowContext executeWorkflow(String originalPrompt) {
        CompiledGraph<MessagesState<String>> workflow = createWorkflow();
        WorkflowContext initialContext = WorkflowContext.builder()
                .originalPrompt(originalPrompt)
                .currentStep("初始化")
                .build();

        GraphRepresentation graph = workflow.getGraph(GraphRepresentation.Type.MERMAID);
        log.info("并发工作流图：\n{}", graph.content());
        log.info("开始执行并发代码生成工作流");

        // 为四条图片分支准备独立线程池，队列容量限制突发任务的内存占用。
        ExecutorService pool = ExecutorBuilder.create()
                .setCorePoolSize(10)
                .setMaxPoolSize(20)
                .setWorkQueue(new LinkedBlockingQueue<>(100))
                .setThreadFactory(ThreadFactoryBuilder.create()
                        .setNamePrefix("Parallel-Image-Collect")
                        .build())
                .build();
        RunnableConfig runnableConfig = RunnableConfig.builder()
                .addParallelNodeExecutor(IMAGE_PLAN, pool)
                .build();

        WorkflowContext finalContext = null;
        int stepCounter = 1;
        try {
            for (NodeOutput<MessagesState<String>> step : workflow.stream(
                    WorkflowContext.saveContext(initialContext), runnableConfig)) {
                log.info("第 {} 步完成", stepCounter++);
                WorkflowContext currentContext = WorkflowContext.getContext(step.state());
                if (currentContext != null) {
                    finalContext = currentContext;
                    log.info("当前步骤上下文：{}", currentContext);
                }
            }
        } finally {
            // 所有分支已经完成后关闭线程池，释放工作线程和排队资源。
            pool.shutdown();
        }
        log.info("并发代码生成工作流执行完成");
        return finalContext;
    }

    /**
     * 根据质量检查结果决定修复、构建或结束。
     */
    private String routeAfterQualityCheck(MessagesState<String> state) {
        WorkflowContext context = WorkflowContext.getContext(state);
        QualityResult qualityResult = context.getQualityResult();
        if (qualityResult == null || !qualityResult.getIsValid()) {
            log.error("代码质量检查失败，需要重新生成代码");
            return QUALITY_FAIL_ROUTE;
        }

        log.info("代码质量检查通过，继续后续流程");
        CodeGenTypeEnum generationType = context.getGenerationType();
        if (generationType == CodeGenTypeEnum.VUE_PROJECT) {
            return BUILD_ROUTE;
        }
        return SKIP_BUILD_ROUTE;
    }
}
