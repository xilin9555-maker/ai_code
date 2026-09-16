package com.tmz.aicode.langgraph4j.node.concurrent;

import com.tmz.aicode.langgraph4j.model.ImageCollectionPlan;
import com.tmz.aicode.langgraph4j.model.ImageResource;
import com.tmz.aicode.langgraph4j.state.WorkflowContext;
import com.tmz.aicode.langgraph4j.tools.MermaidDiagramTool;
import com.tmz.aicode.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.util.ArrayList;
import java.util.List;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 架构图并发分支节点。
 *
 * 节点读取 diagramTasks，把每项 Mermaid 文本转换为图片并上传对象存储，结果写入
 * diagrams 中间字段，供汇聚节点读取。
 */
@Slf4j
public final class DiagramCollectorNode {

    /** 工作流状态中用于展示的步骤名称。 */
    public static final String STEP_NAME = "架构图生成";

    private DiagramCollectorNode() {
        // 节点通过静态工厂创建，不需要保存实例状态。
    }

    /** 创建架构图生成节点。 */
    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = requireContext(state);
            List<ImageResource> diagrams = new ArrayList<>();
            try {
                ImageCollectionPlan plan = context.getImageCollectionPlan();
                if (plan != null && plan.getDiagramTasks() != null) {
                    MermaidDiagramTool diagramTool =
                            SpringContextUtil.getBean(MermaidDiagramTool.class);
                    log.info("开始并发生成架构图，任务数：{}", plan.getDiagramTasks().size());
                    for (ImageCollectionPlan.DiagramTask task : plan.getDiagramTasks()) {
                        List<ImageResource> images = diagramTool.generateMermaidDiagram(
                                task.mermaidCode(), task.description());
                        if (images != null) {
                            diagrams.addAll(images);
                        }
                    }
                    log.info("架构图生成完成，共生成 {} 张图片", diagrams.size());
                }
            } catch (Exception exception) {
                log.error("架构图生成失败：{}", exception.getMessage(), exception);
            }
            context.setDiagrams(diagrams);
            context.setCurrentStep(STEP_NAME);
            return WorkflowContext.saveContext(context);
        });
    }

    /** 读取共享上下文，并在入口处报告缺少初始化状态的问题。 */
    private static WorkflowContext requireContext(MessagesState<String> state) {
        WorkflowContext context = WorkflowContext.getContext(state);
        if (context == null) {
            throw new IllegalStateException("架构图生成节点缺少工作流上下文");
        }
        return context;
    }
}
