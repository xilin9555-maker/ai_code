package com.tmz.aicode.langgraph4j.node.concurrent;

import com.tmz.aicode.langgraph4j.model.ImageCollectionPlan;
import com.tmz.aicode.langgraph4j.model.ImageResource;
import com.tmz.aicode.langgraph4j.state.WorkflowContext;
import com.tmz.aicode.langgraph4j.tools.UndrawIllustrationTool;
import com.tmz.aicode.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.util.ArrayList;
import java.util.List;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 插画图片并发分支节点。
 *
 * 节点读取 illustrationTasks 并调用插画搜索工具，结果保存在 illustrations 中间字段中，
 * 等待图片聚合节点统一汇总。
 */
@Slf4j
public final class IllustrationCollectorNode {

    /** 工作流状态中用于展示的步骤名称。 */
    public static final String STEP_NAME = "插画图片收集";

    private IllustrationCollectorNode() {
        // 节点通过静态工厂创建，不需要保存实例状态。
    }

    /** 创建插画图片收集节点。 */
    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = requireContext(state);
            List<ImageResource> illustrations = new ArrayList<>();
            try {
                ImageCollectionPlan plan = context.getImageCollectionPlan();
                if (plan != null && plan.getIllustrationTasks() != null) {
                    UndrawIllustrationTool illustrationTool =
                            SpringContextUtil.getBean(UndrawIllustrationTool.class);
                    log.info("开始并发收集插画图片，任务数：{}",
                            plan.getIllustrationTasks().size());
                    for (ImageCollectionPlan.IllustrationTask task
                            : plan.getIllustrationTasks()) {
                        List<ImageResource> images =
                                illustrationTool.searchIllustrations(task.query());
                        if (images != null) {
                            illustrations.addAll(images);
                        }
                    }
                    log.info("插画图片收集完成，共收集到 {} 张图片", illustrations.size());
                }
            } catch (Exception exception) {
                log.error("插画图片收集失败：{}", exception.getMessage(), exception);
            }
            context.setIllustrations(illustrations);
            context.setCurrentStep(STEP_NAME);
            return WorkflowContext.saveContext(context);
        });
    }

    /** 读取共享上下文，并在入口处报告缺少初始化状态的问题。 */
    private static WorkflowContext requireContext(MessagesState<String> state) {
        WorkflowContext context = WorkflowContext.getContext(state);
        if (context == null) {
            throw new IllegalStateException("插画图片收集节点缺少工作流上下文");
        }
        return context;
    }
}
