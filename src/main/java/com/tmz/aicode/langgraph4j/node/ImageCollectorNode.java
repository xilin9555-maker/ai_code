package com.tmz.aicode.langgraph4j.node;

import com.tmz.aicode.langgraph4j.ai.ImageCollectionPlanService;
import com.tmz.aicode.langgraph4j.model.ImageCollectionPlan;
import com.tmz.aicode.langgraph4j.model.ImageResource;
import com.tmz.aicode.langgraph4j.state.WorkflowContext;
import com.tmz.aicode.langgraph4j.tools.ImageSearchTool;
import com.tmz.aicode.langgraph4j.tools.LogoGeneratorTool;
import com.tmz.aicode.langgraph4j.tools.MermaidDiagramTool;
import com.tmz.aicode.langgraph4j.tools.UndrawIllustrationTool;
import com.tmz.aicode.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.springframework.util.StopWatch;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 图片收集工作节点。
 *
 * 节点先让规划 AI 把网站需求转换成结构化图片任务，再通过 CompletableFuture 并发执行
 * 内容图片搜索、插画搜索、架构图绘制和 Logo 生成，最后汇总图片供提示词增强节点使用。
 */
@Slf4j
public final class ImageCollectorNode {

    /** 工作流状态中用于展示的步骤名称。 */
    public static final String STEP_NAME = "图片收集";

    private ImageCollectorNode() {
        // 节点通过静态工厂创建，不需要保存实例状态。
    }

    /**
     * 创建图片收集节点。
     *
     * @return 可注册到工作流图的异步节点
     */
    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = requireContext(state);
            log.info("执行节点：{}", STEP_NAME);

            String originalPrompt = context.getOriginalPrompt();
            List<ImageResource> collectedImages = new ArrayList<>();
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();
            try {
                // 第一步：由 AI 一次性生成四类图片的结构化收集计划。
                ImageCollectionPlanService planService =
                        SpringContextUtil.getBean(ImageCollectionPlanService.class);
                ImageCollectionPlan plan = planService.planImageCollection(originalPrompt);
                log.info("获取到图片收集计划，开始并发执行");

                // 第二步：每个计划任务创建一个独立 Future，使外部图片请求可以并行等待。
                List<CompletableFuture<List<ImageResource>>> futures = new ArrayList<>();

                // 内容图片任务直接调用关键词搜索工具。
                if (plan != null && plan.getContentImageTasks() != null) {
                    ImageSearchTool imageSearchTool =
                            SpringContextUtil.getBean(ImageSearchTool.class);
                    for (ImageCollectionPlan.ImageSearchTask task
                            : plan.getContentImageTasks()) {
                        futures.add(CompletableFuture.supplyAsync(() ->
                                imageSearchTool.searchContentImages(task.query())));
                    }
                }

                // 插画任务通过公开插画资源库搜索装饰性图片。
                if (plan != null && plan.getIllustrationTasks() != null) {
                    UndrawIllustrationTool illustrationTool =
                            SpringContextUtil.getBean(UndrawIllustrationTool.class);
                    for (ImageCollectionPlan.IllustrationTask task
                            : plan.getIllustrationTasks()) {
                        futures.add(CompletableFuture.supplyAsync(() ->
                                illustrationTool.searchIllustrations(task.query())));
                    }
                }

                // 架构图任务并发调用 Mermaid 转换和对象存储上传流程。
                if (plan != null && plan.getDiagramTasks() != null) {
                    MermaidDiagramTool diagramTool =
                            SpringContextUtil.getBean(MermaidDiagramTool.class);
                    for (ImageCollectionPlan.DiagramTask task : plan.getDiagramTasks()) {
                        futures.add(CompletableFuture.supplyAsync(() ->
                                diagramTool.generateMermaidDiagram(
                                        task.mermaidCode(), task.description())));
                    }
                }

                // Logo 任务并发调用图片生成、临时图片下载和对象存储上传流程。
                if (plan != null && plan.getLogoTasks() != null) {
                    LogoGeneratorTool logoTool =
                            SpringContextUtil.getBean(LogoGeneratorTool.class);
                    for (ImageCollectionPlan.LogoTask task : plan.getLogoTasks()) {
                        futures.add(CompletableFuture.supplyAsync(() ->
                                logoTool.generateLogos(task.description())));
                    }
                }

                // 第三步：等待所有并发任务完成，再按规划顺序汇总各工具返回的图片。
                CompletableFuture<Void> allTasks = CompletableFuture.allOf(
                        futures.toArray(new CompletableFuture<?>[0]));
                allTasks.join();
                for (CompletableFuture<List<ImageResource>> future : futures) {
                    List<ImageResource> images = future.join();
                    if (images != null) {
                        collectedImages.addAll(images);
                    }
                }
                log.info("并发图片收集完成，共收集到 {} 张图片", collectedImages.size());
            } catch (Exception exception) {
                // 规划或收集异常时保留已经初始化的空列表，使后续节点仍能继续生成网站。
                log.error("图片收集失败：{}", exception.getMessage(), exception);
            } finally {
                // 无论收集是否成功都记录总耗时，便于比较不同图片收集实现的性能。
                stopWatch.stop();
                log.info("图片收集总耗时：{} ms", stopWatch.getTotalTimeMillis());
            }

            context.setCurrentStep(STEP_NAME);
            context.setImageList(collectedImages);
            return WorkflowContext.saveContext(context);
        });
    }

    /** 读取共享上下文，并在入口处报告缺少初始化状态的问题。 */
    private static WorkflowContext requireContext(MessagesState<String> state) {
        WorkflowContext context = WorkflowContext.getContext(state);
        if (context == null) {
            throw new IllegalStateException("图片收集节点缺少工作流上下文");
        }
        return context;
    }
}
