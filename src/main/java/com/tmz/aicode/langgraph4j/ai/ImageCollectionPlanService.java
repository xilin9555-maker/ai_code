package com.tmz.aicode.langgraph4j.ai;

import com.tmz.aicode.langgraph4j.model.ImageCollectionPlan;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 图片收集规划 AI 服务。
 *
 * 服务只分析网站需求并返回结构化任务，不直接搜索、绘制或生成图片。具体图片工具由
 * ImageCollectorNode 根据计划并发调用，从而减少模型与工具之间的多轮交互。
 */
public interface ImageCollectionPlanService {

    /**
     * 根据用户的网站需求规划需要收集的图片类别及工具参数。
     *
     * @param userPrompt 用户对网站主题、功能和视觉风格的完整描述
     * @return 按内容图片、插画、架构图和 Logo 分类的结构化任务
     */
    @SystemMessage(fromResource = "prompt/image-collection-plan-system-prompt.txt")
    ImageCollectionPlan planImageCollection(@UserMessage String userPrompt);
}
