package com.tmz.aicode.langgraph4j.node.concurrent;

import com.tmz.aicode.langgraph4j.model.ImageCollectionPlan;
import com.tmz.aicode.langgraph4j.model.ImageResource;
import com.tmz.aicode.langgraph4j.state.WorkflowContext;
import com.tmz.aicode.langgraph4j.tools.ImageSearchTool;
import com.tmz.aicode.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.util.ArrayList;
import java.util.List;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 内容图片并发分支节点。
 *
 * 该节点只消费计划中的 contentImageTasks，并把搜索结果写入 contentImages 中间字段，
 * 不直接修改最终图片列表，避免和其他并发分支争用同一个集合。
 */
@Slf4j
public final class ContentImageCollectorNode {

    /** 工作流状态中用于展示的步骤名称。 */
    public static final String STEP_NAME = "内容图片收集";

    private ContentImageCollectorNode() {
        // 节点通过静态工厂创建，不需要保存实例状态。
    }

    /** 创建内容图片收集节点。 */
    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = requireContext(state);
            List<ImageResource> contentImages = new ArrayList<>();
            try {
                ImageCollectionPlan plan = context.getImageCollectionPlan();
                if (plan != null && plan.getContentImageTasks() != null) {
                    ImageSearchTool imageSearchTool =
                            SpringContextUtil.getBean(ImageSearchTool.class);
                    log.info("开始并发收集内容图片，任务数：{}",
                            plan.getContentImageTasks().size());
                    for (ImageCollectionPlan.ImageSearchTask task
                            : plan.getContentImageTasks()) {
                        List<ImageResource> images =
                                imageSearchTool.searchContentImages(task.query());
                        if (images != null) {
                            contentImages.addAll(images);
                        }
                    }
                    log.info("内容图片收集完成，共收集到 {} 张图片", contentImages.size());
                }
            } catch (Exception exception) {
                log.error("内容图片收集失败：{}", exception.getMessage(), exception);
            }
            context.setContentImages(contentImages);
            context.setCurrentStep(STEP_NAME);
            return WorkflowContext.saveContext(context);
        });
    }

    /** 读取共享上下文，并在入口处报告缺少初始化状态的问题。 */
    private static WorkflowContext requireContext(MessagesState<String> state) {
        WorkflowContext context = WorkflowContext.getContext(state);
        if (context == null) {
            throw new IllegalStateException("内容图片收集节点缺少工作流上下文");
        }
        return context;
    }
}
