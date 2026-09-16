package com.tmz.aicode.langgraph4j.node;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.tmz.aicode.langgraph4j.model.ImageResource;
import com.tmz.aicode.langgraph4j.state.WorkflowContext;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.util.List;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 整合原始需求与图片信息的提示词增强节点。
 *
 * 节点不会再次调用模型，而是把上游收集到的图片资源追加到用户原始需求后面，并明确要求
 * 代码生成阶段在合适的位置使用这些图片。
 */
@Slf4j
public final class PromptEnhancerNode {

    /** 工作流状态中用于展示的步骤名称。 */
    public static final String STEP_NAME = "提示词增强";

    private PromptEnhancerNode() {
        // 节点通过静态工厂创建，不需要保存实例状态。
    }

    /**
     * 创建提示词增强节点。
     *
     * @return 可注册到工作流图的异步节点
     */
    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = requireContext(state);
            log.info("执行节点：{}", STEP_NAME);

            String originalPrompt = context.getOriginalPrompt();
            String imageListStr = context.getImageListStr();
            List<ImageResource> imageList = context.getImageList();

            StringBuilder enhancedPromptBuilder = new StringBuilder(originalPrompt);
            if (CollUtil.isNotEmpty(imageList) || StrUtil.isNotBlank(imageListStr)) {
                enhancedPromptBuilder.append("\n\n## 可用素材资源\n");
                enhancedPromptBuilder.append(
                        "请在生成网站使用以下图片资源，将这些图片合理地嵌入到网站的相应位置中。\n");
                if (CollUtil.isNotEmpty(imageList)) {
                    // 结构化图片优先，逐项保留类别、描述和 URL，避免丢失图片语义。
                    for (ImageResource image : imageList) {
                        enhancedPromptBuilder.append("- ")
                                .append(image.getCategory().getText())
                                .append("：")
                                .append(image.getDescription())
                                .append("（")
                                .append(image.getUrl())
                                .append("）\n");
                    }
                } else {
                    // 当前图片收集服务返回文本，直接追加即可，不进行重复解析和转换。
                    enhancedPromptBuilder.append(imageListStr);
                }
            }
            String enhancedPrompt = enhancedPromptBuilder.toString();

            context.setCurrentStep(STEP_NAME);
            context.setEnhancedPrompt(enhancedPrompt);
            log.info("提示词增强完成，增强后长度：{} 字符", enhancedPrompt.length());
            return WorkflowContext.saveContext(context);
        });
    }

    /** 读取共享上下文，并在入口处报告缺少初始化状态的问题。 */
    private static WorkflowContext requireContext(MessagesState<String> state) {
        WorkflowContext context = WorkflowContext.getContext(state);
        if (context == null) {
            throw new IllegalStateException("提示词增强节点缺少工作流上下文");
        }
        return context;
    }
}
