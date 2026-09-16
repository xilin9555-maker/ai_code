package com.tmz.aicode.langgraph4j.node.concurrent;

import com.tmz.aicode.langgraph4j.model.ImageCollectionPlan;
import com.tmz.aicode.langgraph4j.model.ImageResource;
import com.tmz.aicode.langgraph4j.state.WorkflowContext;
import com.tmz.aicode.langgraph4j.tools.LogoGeneratorTool;
import com.tmz.aicode.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.util.ArrayList;
import java.util.List;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * Logo 并发分支节点。
 *
 * 节点读取 logoTasks，依次执行当前分支内的 Logo 生成任务，并把上传后的资源写入 logos
 * 中间字段。该分支会与其他三类图片分支并行执行。
 */
@Slf4j
public final class LogoCollectorNode {

    /** 工作流状态中用于展示的步骤名称。 */
    public static final String STEP_NAME = "Logo 生成";

    private LogoCollectorNode() {
        // 节点通过静态工厂创建，不需要保存实例状态。
    }

    /** 创建 Logo 生成节点。 */
    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = requireContext(state);
            List<ImageResource> logos = new ArrayList<>();
            try {
                ImageCollectionPlan plan = context.getImageCollectionPlan();
                if (plan != null && plan.getLogoTasks() != null) {
                    LogoGeneratorTool logoTool =
                            SpringContextUtil.getBean(LogoGeneratorTool.class);
                    log.info("开始并发生成 Logo，任务数：{}", plan.getLogoTasks().size());
                    for (ImageCollectionPlan.LogoTask task : plan.getLogoTasks()) {
                        List<ImageResource> images = logoTool.generateLogos(task.description());
                        if (images != null) {
                            logos.addAll(images);
                        }
                    }
                    log.info("Logo 生成完成，共生成 {} 张图片", logos.size());
                }
            } catch (Exception exception) {
                log.error("Logo 生成失败：{}", exception.getMessage(), exception);
            }
            context.setLogos(logos);
            context.setCurrentStep(STEP_NAME);
            return WorkflowContext.saveContext(context);
        });
    }

    /** 读取共享上下文，并在入口处报告缺少初始化状态的问题。 */
    private static WorkflowContext requireContext(MessagesState<String> state) {
        WorkflowContext context = WorkflowContext.getContext(state);
        if (context == null) {
            throw new IllegalStateException("Logo 生成节点缺少工作流上下文");
        }
        return context;
    }
}
