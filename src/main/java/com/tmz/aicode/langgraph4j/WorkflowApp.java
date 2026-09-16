package com.tmz.aicode.langgraph4j;

import cn.hutool.core.thread.ExecutorBuilder;
import cn.hutool.core.thread.ThreadFactoryBuilder;
import cn.hutool.json.JSONUtil;
import com.tmz.aicode.ai.model.message.AiResponseMessage;
import com.tmz.aicode.ai.model.message.BuildProgressMessage;
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
import com.tmz.aicode.model.dto.build.BuildProgress;
import com.tmz.aicode.model.enums.CodeGenTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphRepresentation;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.prebuilt.MessagesStateGraph;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;

/**
 * 组合独立工作节点的网站生成工作流入口。
 *
 * 工作流使用四个共享状态的子图并发收集内容图片、插画、架构图和 Logo，聚合图片后再
 * 执行提示词增强、智能路由、代码生成、质量检查和项目构建。质量检查未通过时会沿循环边
 * 回到代码生成节点，直到检查通过后再构建或直接结束。
 */
@Slf4j
public final class WorkflowApp {

    /** 独立运行入口使用的应用 id，业务接口会传入真实应用 id 覆盖该值。 */
    private static final Long DEFAULT_APP_ID = 1L;

    public static final String IMAGE_PLAN = "image_plan";
    public static final String CONTENT_IMAGE_SUBGRAPH = "content_image_subgraph";
    public static final String ILLUSTRATION_SUBGRAPH = "illustration_subgraph";
    public static final String DIAGRAM_SUBGRAPH = "diagram_subgraph";
    public static final String LOGO_SUBGRAPH = "logo_subgraph";
    public static final String IMAGE_AGGREGATOR = "image_aggregator";
    public static final String PROMPT_ENHANCER = "prompt_enhancer";
    public static final String ROUTER = "router";
    public static final String CODE_GENERATOR = "code_generator";
    public static final String CODE_QUALITY_CHECK = "code_quality_check";
    public static final String PROJECT_BUILDER = "project_builder";

    /** 条件边返回该标识时，工作流进入 Vue 项目构建节点。 */
    private static final String BUILD_ROUTE = "build";

    /** 条件边返回该标识时，工作流跳过构建并直接结束。 */
    private static final String SKIP_BUILD_ROUTE = "skip_build";

    /** 质量检查未通过时返回该标识，使工作流重新进入代码生成节点。 */
    private static final String QUALITY_FAIL_ROUTE = "fail";

    /** 内容图片子图内部的节点名称。 */
    private static final String CONTENT_COLLECT = "content_collect";

    /** 插画图片子图内部的节点名称。 */
    private static final String ILLUSTRATION_COLLECT = "illustration_collect";

    /** 架构图子图内部的节点名称。 */
    private static final String DIAGRAM_GENERATE = "diagram_generate";

    /** Logo 子图内部的节点名称。 */
    private static final String LOGO_GENERATE = "logo_generate";

    private WorkflowApp() {
        // 工作流由静态工厂创建，不需要实例化入口类。
    }

    /**
     * 创建并编译使用独立节点类的工作流。
     *
     * @return 可以直接调用或流式执行的工作流
     * @throws GraphStateException 节点或边的定义不合法时抛出
     */
    public static CompiledGraph<MessagesState<String>> createWorkflow()
            throws GraphStateException {
        return createWorkflow(ignored -> {
        }, ignored -> {
        }, ignored -> {
        });
    }

    /**
     * 创建能够转发代码生成内容的工作流。
     *
     * @param codeOutputConsumer 代码生成节点产生的原始流式片段接收器
     * @return 可以直接调用或流式执行的工作流
     * @throws GraphStateException 节点或边的定义不合法时抛出
     */
    public static CompiledGraph<MessagesState<String>> createWorkflow(
            Consumer<String> codeOutputConsumer) throws GraphStateException {
        return createWorkflow(codeOutputConsumer, ignored -> {
        }, ignored -> {
        });
    }

    /**
     * 创建同时支持代码片段和节点进度输出的工作流。
     *
     * LangGraph4j 的 stream 会在节点结束后返回状态，耗时节点执行期间没有新状态。这里在
     * 节点动作外层增加进度回调，使图片收集、代码检查和构建等阶段一开始就能通知前端。
     *
     * @param codeOutputConsumer 代码生成模型产生的原始流式片段接收器
     * @param progressOutputConsumer 节点开始、完成或失败消息的接收器
     * @return 可以流式执行并报告节点进度的工作流
     * @throws GraphStateException 节点或边的定义不合法时抛出
     */
    public static CompiledGraph<MessagesState<String>> createWorkflow(
            Consumer<String> codeOutputConsumer,
            Consumer<String> progressOutputConsumer) throws GraphStateException {
        return createWorkflow(codeOutputConsumer, progressOutputConsumer, ignored -> {
        });
    }

    /**
     * 创建同时支持代码片段、节点进度和 Vue 构建进度的工作流。
     *
     * @param codeOutputConsumer 模型与工具产生的原始消息接收器
     * @param progressOutputConsumer 工作节点状态接收器
     * @param buildProgressConsumer Vue 构建阶段接收器
     * @return 可以流式执行并报告全部进度的工作流
     * @throws GraphStateException 节点或边定义不合法时抛出
     */
    public static CompiledGraph<MessagesState<String>> createWorkflow(
            Consumer<String> codeOutputConsumer,
            Consumer<String> progressOutputConsumer,
            Consumer<BuildProgress> buildProgressConsumer) throws GraphStateException {
        // 未编译子图会直接合并到父图，并与父图完全共享 WorkflowContext 状态。
        StateGraph<MessagesState<String>> contentImageSubgraph =
                createContentImageSubgraph(progressOutputConsumer);
        StateGraph<MessagesState<String>> illustrationSubgraph =
                createIllustrationSubgraph(progressOutputConsumer);
        StateGraph<MessagesState<String>> diagramSubgraph =
                createDiagramSubgraph(progressOutputConsumer);
        StateGraph<MessagesState<String>> logoSubgraph =
                createLogoSubgraph(progressOutputConsumer);

        return new MessagesStateGraph<String>()
                // 父图中的常规业务节点。
                .addNode(IMAGE_PLAN, withProgress(
                        ImagePlanNode.STEP_NAME,
                        ImagePlanNode.create(),
                        progressOutputConsumer))
                .addNode(PROMPT_ENHANCER, withProgress(
                        PromptEnhancerNode.STEP_NAME,
                        PromptEnhancerNode.create(),
                        progressOutputConsumer))
                .addNode(ROUTER, withProgress(
                        RouterNode.STEP_NAME,
                        RouterNode.create(),
                        progressOutputConsumer))
                .addNode(CODE_GENERATOR, withProgress(
                        CodeGeneratorNode.STEP_NAME,
                        CodeGeneratorNode.create(codeOutputConsumer),
                        progressOutputConsumer))
                .addNode(CODE_QUALITY_CHECK, withProgress(
                        CodeQualityCheckNode.STEP_NAME,
                        CodeQualityCheckNode.create(),
                        progressOutputConsumer))
                .addNode(PROJECT_BUILDER, withProgress(
                        ProjectBuilderNode.STEP_NAME,
                        ProjectBuilderNode.create(buildProgressConsumer),
                        progressOutputConsumer))

                // 直接添加未编译子图，使子图节点完全合并到父图并共享全部状态。
                .addNode(CONTENT_IMAGE_SUBGRAPH, contentImageSubgraph)
                .addNode(ILLUSTRATION_SUBGRAPH, illustrationSubgraph)
                .addNode(DIAGRAM_SUBGRAPH, diagramSubgraph)
                .addNode(LOGO_SUBGRAPH, logoSubgraph)
                .addNode(IMAGE_AGGREGATOR, withProgress(
                        ImageAggregatorNode.STEP_NAME,
                        ImageAggregatorNode.create(),
                        progressOutputConsumer))

                .addEdge(START, IMAGE_PLAN)

                // 图片计划节点同时连接四个子图，形成并发执行的子图分支。
                .addEdge(IMAGE_PLAN, CONTENT_IMAGE_SUBGRAPH)
                .addEdge(IMAGE_PLAN, ILLUSTRATION_SUBGRAPH)
                .addEdge(IMAGE_PLAN, DIAGRAM_SUBGRAPH)
                .addEdge(IMAGE_PLAN, LOGO_SUBGRAPH)

                // 所有子图完成后统一进入图片聚合节点。
                .addEdge(CONTENT_IMAGE_SUBGRAPH, IMAGE_AGGREGATOR)
                .addEdge(ILLUSTRATION_SUBGRAPH, IMAGE_AGGREGATOR)
                .addEdge(DIAGRAM_SUBGRAPH, IMAGE_AGGREGATOR)
                .addEdge(LOGO_SUBGRAPH, IMAGE_AGGREGATOR)

                // 聚合完成后继续执行原有的串行代码生成流程。
                .addEdge(IMAGE_AGGREGATOR, PROMPT_ENHANCER)
                .addEdge(PROMPT_ENHANCER, ROUTER)
                .addEdge(ROUTER, CODE_GENERATOR)
                .addEdge(CODE_GENERATOR, CODE_QUALITY_CHECK)
                // 检查失败时重新生成；检查通过后根据生成类型决定构建或直接结束。
                .addConditionalEdges(
                        CODE_QUALITY_CHECK,
                        edge_async(WorkflowApp::routeAfterQualityCheck),
                        Map.of(
                                BUILD_ROUTE, PROJECT_BUILDER,
                                SKIP_BUILD_ROUTE, END,
                                QUALITY_FAIL_ROUTE, CODE_GENERATOR
                        )
                )
                .addEdge(PROJECT_BUILDER, END)
                .compile();
    }

    /**
     * 在不改变节点业务逻辑的情况下补充实时进度通知。
     */
    static AsyncNodeAction<MessagesState<String>> withProgress(
            String stepName,
            AsyncNodeAction<MessagesState<String>> nodeAction,
            Consumer<String> progressOutputConsumer) {
        return state -> {
            reportProgress(progressOutputConsumer,
                    "[AI 工作流] 开始执行：" + stepName);
            try {
                return nodeAction.apply(state).whenComplete((result, error) -> {
                    if (error == null) {
                        reportProgress(progressOutputConsumer,
                                "[AI 工作流] 已完成：" + stepName);
                    } else {
                        reportProgress(progressOutputConsumer,
                                "[AI 工作流] 执行失败：" + stepName + " - "
                                        + getErrorMessage(error));
                    }
                });
            } catch (RuntimeException exception) {
                reportProgress(progressOutputConsumer,
                        "[AI 工作流] 执行失败：" + stepName + " - "
                                + getErrorMessage(exception));
                throw exception;
            }
        };
    }

    /** 进度展示失败不能反向中断真正的工作节点。 */
    private static void reportProgress(Consumer<String> progressOutputConsumer,
                                       String message) {
        try {
            progressOutputConsumer.accept(message);
        } catch (RuntimeException exception) {
            log.warn("发送工作流进度失败：{}", exception.getMessage());
        }
    }

    /** 从异步包装异常中提取便于展示的实际错误信息。 */
    private static String getErrorMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() == null ? "未知错误" : current.getMessage();
    }

    /**
     * 创建内容图片收集子图。
     *
     * @return 与父图共享完整状态的未编译子图
     */
    private static StateGraph<MessagesState<String>> createContentImageSubgraph(
            Consumer<String> progressOutputConsumer)
            throws GraphStateException {
        return new MessagesStateGraph<String>()
                .addNode(CONTENT_COLLECT, withProgress(
                        ContentImageCollectorNode.STEP_NAME,
                        ContentImageCollectorNode.create(),
                        progressOutputConsumer))
                .addEdge(START, CONTENT_COLLECT)
                .addEdge(CONTENT_COLLECT, END);
    }

    /**
     * 创建插画图片收集子图。
     *
     * @return 与父图共享完整状态的未编译子图
     */
    private static StateGraph<MessagesState<String>> createIllustrationSubgraph(
            Consumer<String> progressOutputConsumer)
            throws GraphStateException {
        return new MessagesStateGraph<String>()
                .addNode(ILLUSTRATION_COLLECT, withProgress(
                        IllustrationCollectorNode.STEP_NAME,
                        IllustrationCollectorNode.create(),
                        progressOutputConsumer))
                .addEdge(START, ILLUSTRATION_COLLECT)
                .addEdge(ILLUSTRATION_COLLECT, END);
    }

    /**
     * 创建架构图生成子图。
     *
     * @return 与父图共享完整状态的未编译子图
     */
    private static StateGraph<MessagesState<String>> createDiagramSubgraph(
            Consumer<String> progressOutputConsumer)
            throws GraphStateException {
        return new MessagesStateGraph<String>()
                .addNode(DIAGRAM_GENERATE, withProgress(
                        DiagramCollectorNode.STEP_NAME,
                        DiagramCollectorNode.create(),
                        progressOutputConsumer))
                .addEdge(START, DIAGRAM_GENERATE)
                .addEdge(DIAGRAM_GENERATE, END);
    }

    /**
     * 创建 Logo 生成子图。
     *
     * @return 与父图共享完整状态的未编译子图
     */
    private static StateGraph<MessagesState<String>> createLogoSubgraph(
            Consumer<String> progressOutputConsumer)
            throws GraphStateException {
        return new MessagesStateGraph<String>()
                .addNode(LOGO_GENERATE, withProgress(
                        LogoCollectorNode.STEP_NAME,
                        LogoCollectorNode.create(),
                        progressOutputConsumer))
                .addEdge(START, LOGO_GENERATE)
                .addEdge(LOGO_GENERATE, END);
    }

    /**
     * 决定代码生成完成后是否需要执行项目构建。
     *
     * HTML 和多文件模式在代码生成节点结束时已经保存为可直接访问的网站文件，因此可以
     * 直接结束工作流。Vue 工程包含单文件组件和模块依赖，必须继续执行依赖安装和生产构建。
     *
     * @param state 已完成代码生成的工作流状态
     * @return 条件边映射表中定义的构建或跳过标识
     */
    private static String routeBuildOrSkip(MessagesState<String> state) {
        WorkflowContext context = WorkflowContext.getContext(state);
        CodeGenTypeEnum generationType = context.getGenerationType();
        if (generationType == CodeGenTypeEnum.HTML
                || generationType == CodeGenTypeEnum.MULTI_FILE) {
            return SKIP_BUILD_ROUTE;
        }
        return BUILD_ROUTE;
    }

    /**
     * 根据质量检查结果决定继续后续流程还是返回代码生成节点修复问题。
     *
     * 检查结果为空或明确未通过时，工作流沿循环边重新生成代码。检查通过后复用构建路由，
     * Vue 工程进入构建节点，HTML 和多文件模式直接结束。
     *
     * @param state 已完成代码质量检查的工作流状态
     * @return 失败、构建或跳过构建三种路由标识之一
     */
    private static String routeAfterQualityCheck(MessagesState<String> state) {
        WorkflowContext context = WorkflowContext.getContext(state);
        QualityResult qualityResult = context.getQualityResult();
        if (qualityResult == null || !qualityResult.getIsValid()) {
            log.error("代码质量检查失败，需要重新生成代码");
            return QUALITY_FAIL_ROUTE;
        }
        log.info("代码质量检查通过，继续后续流程");
        return routeBuildOrSkip(state);
    }

    /**
     * 创建只包含工作流起始输入的上下文。
     *
     * @param originalPrompt 用户提交的原始需求
     * @return 可作为工作流初始状态的上下文
     */
    public static WorkflowContext createInitialContext(String originalPrompt) {
        return createInitialContext(originalPrompt, DEFAULT_APP_ID, null);
    }

    /**
     * 创建接入已有应用的工作流上下文。
     *
     * @param originalPrompt 用户提交的原始需求
     * @param appId 当前应用 id
     * @param generationType 应用已经确定的代码生成类型
     * @return 包含业务参数的初始上下文
     */
    public static WorkflowContext createInitialContext(String originalPrompt,
                                                       Long appId,
                                                       CodeGenTypeEnum generationType) {
        if (appId == null || appId <= 0) {
            throw new IllegalArgumentException("应用 id 必须大于 0");
        }
        return WorkflowContext.builder()
                .appId(appId)
                .originalPrompt(originalPrompt)
                .generationType(generationType)
                .currentStep("初始化")
                .build();
    }

    /**
     * 执行采用并发子图收集图片的完整工作流。
     *
     * 当前 LangGraph4j 版本需要通过 RunnableConfig 为分支起点绑定执行器，四个图片子图
     * 才会真正并发调度。执行器只属于本次运行，并在所有节点结束后关闭。
     *
     * @param originalPrompt 用户提交的原始网站需求
     * @return 最后一个完成节点所产生的工作流上下文
     * @throws GraphStateException 父图或任一子图无法通过编译校验时抛出
     */
    public static WorkflowContext executeWorkflow(String originalPrompt)
            throws GraphStateException {
        return executeWorkflow(originalPrompt, DEFAULT_APP_ID, null);
    }

    /**
     * 使用指定应用参数同步执行完整工作流。
     *
     * @param originalPrompt 用户提交的原始网站需求
     * @param appId 当前应用 id
     * @param generationType 应用已经确定的代码生成类型，为空时由路由节点选择
     * @return 最后一个完成节点产生的工作流上下文
     * @throws GraphStateException 工作流图无法通过编译校验时抛出
     */
    public static WorkflowContext executeWorkflow(String originalPrompt,
                                                  Long appId,
                                                  CodeGenTypeEnum generationType)
            throws GraphStateException {
        CompiledGraph<MessagesState<String>> workflow = createWorkflow();
        WorkflowContext initialContext = createInitialContext(
                originalPrompt, appId, generationType);
        GraphRepresentation graph = workflow.getGraph(GraphRepresentation.Type.MERMAID);
        log.info("子图工作流图：\n{}", graph.content());
        log.info("开始执行子图代码生成工作流");

        ExecutorService pool = createImageCollectionExecutor();
        RunnableConfig runnableConfig = createRunnableConfig(pool);

        WorkflowContext finalContext = null;
        int stepCounter = 1;
        try {
            for (NodeOutput<MessagesState<String>> step : workflow.stream(
                    WorkflowContext.saveContext(initialContext), runnableConfig)) {
                WorkflowContext currentContext = WorkflowContext.getContext(step.state());
                if (currentContext != null) {
                    finalContext = currentContext;
                    log.info("第 {} 步完成，节点：{}，当前上下文：{}",
                            stepCounter++, step.node(), currentContext);
                }
            }
        } finally {
            // 分支任务已经汇聚完成，及时释放本次执行创建的工作线程。
            pool.shutdown();
        }
        log.info("子图代码生成工作流执行完成");
        return finalContext;
    }

    /**
     * 以现有聊天接口能够处理的文本格式执行工作流。
     *
     * 代码生成节点产生的原始片段会直接转发；工作流进度则根据生成类型转换。Vue 工程
     * 使用统一 JSON 消息，HTML 和多文件模式使用普通文本，因此后续可以继续复用原有
     * 流处理器、历史保存和 SSE 包装逻辑。
     *
     * @param originalPrompt 用户提交的原始网站需求
     * @param appId 当前应用 id
     * @param generationType 应用已经确定的代码生成类型
     * @return 可以交给现有流处理器的内容流
     */
    public static Flux<String> executeWorkflowWithFlux(String originalPrompt,
                                                        Long appId,
                                                        CodeGenTypeEnum generationType) {
        if (generationType == null) {
            return Flux.error(new IllegalArgumentException("生成类型不能为空"));
        }
        return Flux.create(sink -> Thread.startVirtualThread(() -> {
            ExecutorService pool = null;
            try {
                Consumer<String> codeOutputConsumer = chunk -> {
                    if (chunk != null && !sink.isCancelled()) {
                        sink.next(chunk);
                    }
                };
                Consumer<String> progressOutputConsumer = message ->
                        emitCompatibleProgress(sink, generationType, message);
                Consumer<BuildProgress> buildProgressConsumer = progress -> {
                    if (!sink.isCancelled()) {
                        sink.next(JSONUtil.toJsonStr(new BuildProgressMessage(progress)));
                    }
                };
                CompiledGraph<MessagesState<String>> workflow =
                        createWorkflow(
                                codeOutputConsumer,
                                progressOutputConsumer,
                                buildProgressConsumer
                        );
                WorkflowContext initialContext = createInitialContext(
                        originalPrompt, appId, generationType);

                emitCompatibleProgress(
                        sink, generationType, "[AI 工作流] 开始分析需求");
                GraphRepresentation graph = workflow.getGraph(GraphRepresentation.Type.MERMAID);
                log.info("应用 {} 的子图工作流图：\n{}", appId, graph.content());

                pool = createImageCollectionExecutor();
                RunnableConfig runnableConfig = createRunnableConfig(pool);

                int stepCounter = 1;
                for (NodeOutput<MessagesState<String>> step : workflow.stream(
                        WorkflowContext.saveContext(initialContext), runnableConfig)) {
                    WorkflowContext currentContext = WorkflowContext.getContext(step.state());
                    if (currentContext != null) {
                        log.info("应用 {} 的第 {} 步完成，节点：{}，当前步骤：{}",
                                appId, stepCounter, step.node(), currentContext.getCurrentStep());
                    }
                    stepCounter++;
                }

                emitCompatibleProgress(
                        sink, generationType, "[AI 工作流] 全部步骤执行完成");
                if (!sink.isCancelled()) {
                    sink.complete();
                }
            } catch (Exception exception) {
                String errorMessage = exception.getMessage() == null
                        ? "未知错误"
                        : exception.getMessage();
                log.error("应用 {} 的 AI 工作流执行失败：{}", appId, errorMessage, exception);
                emitCompatibleProgress(
                        sink, generationType, "[AI 工作流] 执行失败：" + errorMessage);
                if (!sink.isCancelled()) {
                    sink.error(exception);
                }
            } finally {
                if (pool != null) {
                    pool.shutdown();
                }
            }
        }));
    }

    /**
     * 把节点进度转换为当前生成类型对应的流消息格式。
     */
    private static void emitCompatibleProgress(reactor.core.publisher.FluxSink<String> sink,
                                               CodeGenTypeEnum generationType,
                                               String message) {
        if (sink.isCancelled()) {
            return;
        }
        String content = "\n\n" + message + "\n\n";
        if (generationType == CodeGenTypeEnum.VUE_PROJECT) {
            sink.next(JSONUtil.toJsonStr(new AiResponseMessage(content)));
            return;
        }
        sink.next(content);
    }

    /**
     * 以 Flux 形式执行工作流并持续输出具名 SSE 事件。
     *
     * 工作流的同步遍历放入虚拟线程，避免占用处理 HTTP 请求的线程。每当一个节点完成时，
     * 响应流都会发送一次 step_completed；整体流程还会依次发送开始、完成或失败事件。
     *
     * @param originalPrompt 用户提交的原始网站需求
     * @return 包含工作流执行进度的 SSE 响应流
     */
    public static Flux<ServerSentEvent<String>> executeWorkflowWithFlux(String originalPrompt) {
        return Flux.create(sink -> Thread.startVirtualThread(() -> {
            ExecutorService pool = null;
            try {
                CompiledGraph<MessagesState<String>> workflow = createWorkflow();
                WorkflowContext initialContext = createInitialContext(originalPrompt);

                sink.next(formatSseEvent("workflow_start", Map.of(
                        "message", "开始执行代码生成工作流",
                        "originalPrompt", originalPrompt
                )));

                GraphRepresentation graph = workflow.getGraph(GraphRepresentation.Type.MERMAID);
                log.info("子图工作流图：\n{}", graph.content());
                log.info("开始执行 Flux 子图代码生成工作流");

                // 子图仍由专用执行器并发运行，Flux 只负责把执行进度持续交给客户端。
                pool = createImageCollectionExecutor();
                RunnableConfig runnableConfig = createRunnableConfig(pool);

                int stepCounter = 1;
                for (NodeOutput<MessagesState<String>> step : workflow.stream(
                        WorkflowContext.saveContext(initialContext), runnableConfig)) {
                    WorkflowContext currentContext = WorkflowContext.getContext(step.state());
                    if (currentContext != null) {
                        sink.next(formatSseEvent("step_completed", Map.of(
                                "stepNumber", stepCounter,
                                "currentStep", currentContext.getCurrentStep()
                        )));
                        log.info("第 {} 步完成，节点：{}，当前上下文：{}",
                                stepCounter, step.node(), currentContext);
                    }
                    stepCounter++;
                }

                sink.next(formatSseEvent("workflow_completed", Map.of(
                        "message", "代码生成工作流执行完成"
                )));
                log.info("Flux 子图代码生成工作流执行完成");
                sink.complete();
            } catch (Exception exception) {
                String errorMessage = exception.getMessage() == null
                        ? "未知错误"
                        : exception.getMessage();
                log.error("Flux 子图代码生成工作流执行失败：{}", errorMessage, exception);
                sink.next(formatSseEvent("workflow_error", Map.of(
                        "error", errorMessage,
                        "message", "工作流执行失败"
                )));
                sink.error(exception);
            } finally {
                if (pool != null) {
                    // 工作流结束或异常退出时都释放图片子图使用的线程池。
                    pool.shutdown();
                }
            }
        }));
    }

    /**
     * 创建本次工作流执行使用的图片子图线程池。
     *
     * @return 用于调度四条图片子图分支的执行器
     */
    private static ExecutorService createImageCollectionExecutor() {
        return ExecutorBuilder.create()
                .setCorePoolSize(10)
                .setMaxPoolSize(20)
                .setWorkQueue(new LinkedBlockingQueue<>(100))
                .setThreadFactory(ThreadFactoryBuilder.create()
                        .setNamePrefix("Subgraph-Image-Collect")
                        .build())
                .build();
    }

    /**
     * 把图片子图执行器绑定到产生四条并行边的图片计划节点。
     *
     * @param pool 图片子图共用的执行器
     * @return 当前工作流运行所需的动态配置
     */
    private static RunnableConfig createRunnableConfig(ExecutorService pool) {
        return RunnableConfig.builder()
                .addParallelNodeExecutor(IMAGE_PLAN, pool)
                .build();
    }

    /**
     * 把事件名称和业务数据转换成标准 SSE 事件。
     *
     * 业务数据先转换为 JSON，确保其中的中文、换行和引号不会破坏响应结构。事件的协议
     * 编码由 Spring 统一完成，避免已经格式化的字符串被框架再次包装。
     *
     * @param eventType 客户端监听的事件名称
     * @param data 需要随事件发送的业务数据
     * @return 包含事件名称和 JSON 数据的 SSE 事件
     */
    private static ServerSentEvent<String> formatSseEvent(String eventType, Object data) {
        try {
            String jsonData = JSONUtil.toJsonStr(data);
            return ServerSentEvent.<String>builder()
                    .event(eventType)
                    .data(jsonData)
                    .build();
        } catch (Exception exception) {
            log.error("格式化 SSE 事件失败：{}", exception.getMessage(), exception);
            return ServerSentEvent.<String>builder()
                    .event("workflow_error")
                    .data("{\"error\":\"事件格式化失败\"}")
                    .build();
        }
    }

    /**
     * 从命令行执行一次完整子图工作流。
     *
     * @param args 命令行参数，当前未使用
     * @throws GraphStateException 工作流图无法通过编译校验时抛出
     */
    public static void main(String[] args) throws GraphStateException {
        WorkflowContext result = executeWorkflow("创建一个个人作品展示网站");
        log.info("工作流最终上下文：{}", result);
    }
}
