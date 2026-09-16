package com.tmz.aicode.langgraph4j.node.concurrent;

import com.tmz.aicode.langgraph4j.ai.ImageCollectionPlanService;
import com.tmz.aicode.langgraph4j.ai.ImageCollectionPlanServiceFactory;
import com.tmz.aicode.langgraph4j.model.ImageCollectionPlan;
import com.tmz.aicode.langgraph4j.state.WorkflowContext;
import com.tmz.aicode.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 并发图片流程的规划节点。
 *
 * 节点只调用一次规划 AI，把用户需求转换为四类结构化任务。后续并发分支读取同一份计划，
 * 分别执行内容图片、插画、架构图和 Logo 的收集操作。
 */
@Slf4j
public final class ImagePlanNode {

    /** 工作流状态中用于展示的步骤名称。 */
    public static final String STEP_NAME = "图片计划";

    private ImagePlanNode() {
        // 节点通过静态工厂创建，不需要保存实例状态。
    }

    /**
     * 创建图片规划节点。
     *
     * @return 可注册到工作流图的异步节点
     */
    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = requireContext(state);
            String originalPrompt = context.getOriginalPrompt();
            try {
                ImageCollectionPlanServiceFactory planServiceFactory =
                        SpringContextUtil.getBean(ImageCollectionPlanServiceFactory.class);
                ImageCollectionPlanService planService =
                        planServiceFactory.createImageCollectionPlanService();
                ImageCollectionPlan plan = planService.planImageCollection(originalPrompt);
                log.info("生成图片收集计划，准备启动并发分支");
                context.setImageCollectionPlan(plan);
                context.setCurrentStep(STEP_NAME);
            } catch (Exception exception) {
                // 规划失败时保留空计划，四个收集分支会返回空列表并正常进入聚合节点。
                log.error("图片计划生成失败：{}", exception.getMessage(), exception);
            }
            return WorkflowContext.saveContext(context);
        });
    }

    /** 读取共享上下文，并在入口处报告缺少初始化状态的问题。 */
    private static WorkflowContext requireContext(MessagesState<String> state) {
        WorkflowContext context = WorkflowContext.getContext(state);
        if (context == null) {
            throw new IllegalStateException("图片规划节点缺少工作流上下文");
        }
        return context;
    }
}
