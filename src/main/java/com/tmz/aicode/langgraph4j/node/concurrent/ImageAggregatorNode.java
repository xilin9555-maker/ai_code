package com.tmz.aicode.langgraph4j.node.concurrent;

import com.tmz.aicode.langgraph4j.model.ImageResource;
import com.tmz.aicode.langgraph4j.state.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.util.ArrayList;
import java.util.List;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 并发图片结果聚合节点。
 *
 * LangGraph4j 会在四个图片分支全部结束后进入该节点。节点读取各分支独立保存的中间结果，
 * 合并成最终 imageList，之后串行进入提示词增强节点。
 */
@Slf4j
public final class ImageAggregatorNode {

    /** 工作流状态中用于展示的步骤名称。 */
    public static final String STEP_NAME = "图片聚合";

    private ImageAggregatorNode() {
        // 节点通过静态工厂创建，不需要保存实例状态。
    }

    /** 创建图片结果聚合节点。 */
    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = requireContext(state);
            List<ImageResource> allImages = new ArrayList<>();
            log.info("开始聚合并发收集的图片");

            // 各分支只维护自己的字段，因此聚合时无需对共享集合加锁。
            if (context.getContentImages() != null) {
                allImages.addAll(context.getContentImages());
            }
            if (context.getIllustrations() != null) {
                allImages.addAll(context.getIllustrations());
            }
            if (context.getDiagrams() != null) {
                allImages.addAll(context.getDiagrams());
            }
            if (context.getLogos() != null) {
                allImages.addAll(context.getLogos());
            }

            log.info("图片聚合完成，总共 {} 张图片", allImages.size());
            context.setImageList(allImages);
            context.setCurrentStep(STEP_NAME);
            return WorkflowContext.saveContext(context);
        });
    }

    /** 读取共享上下文，并在入口处报告缺少初始化状态的问题。 */
    private static WorkflowContext requireContext(MessagesState<String> state) {
        WorkflowContext context = WorkflowContext.getContext(state);
        if (context == null) {
            throw new IllegalStateException("图片聚合节点缺少工作流上下文");
        }
        return context;
    }
}
