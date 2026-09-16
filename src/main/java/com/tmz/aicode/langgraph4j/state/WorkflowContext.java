package com.tmz.aicode.langgraph4j.state;

import com.tmz.aicode.langgraph4j.model.ImageResource;
import com.tmz.aicode.langgraph4j.model.ImageCollectionPlan;
import com.tmz.aicode.langgraph4j.model.QualityResult;
import com.tmz.aicode.model.enums.CodeGenTypeEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 网站生成工作流在全部节点之间共享的业务状态。
 *
 * LangGraph4j 负责节点调度，WorkflowContext 则集中保存每一步的输入和输出。把业务字段放进
 * 一个上下文对象中，可以避免节点逐个传递大量参数，也为后续检查点恢复和错误排查提供统一入口。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowContext implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** WorkflowContext 在 MessagesState 数据映射中的固定键。 */
    public static final String WORKFLOW_CONTEXT_KEY = "workflowContext";

    /** 当前正在执行或最近完成的节点名称。 */
    private String currentStep;

    /** 当前工作流所属的应用 id，用于复用应用目录和代码生成会话。 */
    private Long appId;

    /** 用户最初输入的网站生成需求。 */
    private String originalPrompt;

    /** 图片列表的文本形式，便于直接拼接进模型提示词。 */
    private String imageListStr;

    /** 图片收集节点获得的结构化图片资源。 */
    private List<ImageResource> imageList;

    /** 图片规划节点生成的结构化收集任务，供四个并发分支共同读取。 */
    private ImageCollectionPlan imageCollectionPlan;

    /** 内容图片收集分支产生的中间结果。 */
    private List<ImageResource> contentImages;

    /** 插画图片收集分支产生的中间结果。 */
    private List<ImageResource> illustrations;

    /** 架构图生成分支产生的中间结果。 */
    private List<ImageResource> diagrams;

    /** Logo 生成分支产生的中间结果。 */
    private List<ImageResource> logos;

    /** 结合图片信息和生成规则改写后的完整提示词。 */
    private String enhancedPrompt;

    /** 智能路由节点选择的代码生成方式。 */
    private CodeGenTypeEnum generationType;

    /** 代码生成节点写入源代码后的目录。 */
    private String generatedCodeDir;

    /** 项目构建节点产出的可部署文件目录。 */
    private String buildResultDir;

    /** 代码质量检查节点返回的结构化检查结果。 */
    private QualityResult qualityResult;

    /** 工作流失败时记录的错误信息，便于统一展示和排查。 */
    private String errorMessage;

    /**
     * 从 LangGraph4j 状态中读取业务上下文。
     *
     * @param state 当前节点接收到的工作流状态
     * @return 已保存的业务上下文；尚未放入上下文时返回 {@code null}
     * @throws IllegalStateException 固定键对应的数据不是 WorkflowContext 时抛出
     */
    public static WorkflowContext getContext(MessagesState<String> state) {
        Objects.requireNonNull(state, "工作流状态不能为空");
        Object context = state.data().get(WORKFLOW_CONTEXT_KEY);
        if (context == null) {
            return null;
        }
        if (!(context instanceof WorkflowContext workflowContext)) {
            throw new IllegalStateException("工作流上下文类型错误：" + context.getClass().getName());
        }
        return workflowContext;
    }

    /**
     * 把业务上下文包装成节点可以返回的状态更新映射。
     *
     * @param context 需要保存的业务上下文
     * @return 以固定键保存上下文的不可变映射
     */
    public static Map<String, Object> saveContext(WorkflowContext context) {
        return Map.of(WORKFLOW_CONTEXT_KEY,
                Objects.requireNonNull(context, "工作流上下文不能为空"));
    }
}
