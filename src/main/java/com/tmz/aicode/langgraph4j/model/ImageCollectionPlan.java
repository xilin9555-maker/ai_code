package com.tmz.aicode.langgraph4j.model;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 根据网站需求生成的图片收集计划。
 *
 * 规划 AI 只负责确定图片类别和工具参数，图片收集节点再依据这些任务并发调用对应工具。
 * 使用结构化计划可以把模型分析与外部资源获取分开，减少围绕工具调用的多轮模型交互。
 */
@Data
public class ImageCollectionPlan implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 通过关键词搜索网站主要内容配图的任务列表。 */
    private List<ImageSearchTask> contentImageTasks;

    /** 从插画资源库搜索装饰性图片的任务列表。 */
    private List<IllustrationTask> illustrationTasks;

    /** 根据 Mermaid 文本绘制架构图的任务列表。 */
    private List<DiagramTask> diagramTasks;

    /** 根据品牌和视觉描述生成 Logo 的任务列表。 */
    private List<LogoTask> logoTasks;

    /**
     * 内容图片搜索任务。
     *
     * @param query 传递给内容图片搜索工具的关键词
     */
    public record ImageSearchTask(String query) implements Serializable {
    }

    /**
     * 插画图片搜索任务。
     *
     * @param query 传递给插画搜索工具的关键词
     */
    public record IllustrationTask(String query) implements Serializable {
    }

    /**
     * 架构图生成任务。
     *
     * @param mermaidCode 完整的 Mermaid 图表代码
     * @param description 架构图的内容和使用场景描述
     */
    public record DiagramTask(
            String mermaidCode,
            String description
    ) implements Serializable {
    }

    /**
     * Logo 生成任务。
     *
     * @param description 品牌、行业、配色和视觉风格描述
     */
    public record LogoTask(String description) implements Serializable {
    }
}
