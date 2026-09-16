package com.tmz.aicode.langgraph4j.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 代码质量检查结果。
 *
 * 该对象既是 AI 服务的结构化返回值，也是工作流中质量检查节点与条件边之间传递的数据。
 * 实现 Serializable 后，可以随 WorkflowContext 一起参与工作流状态复制和持久化。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QualityResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 是否通过质量检查。 */
    private Boolean isValid;

    /** 必须修复并可能导致项目无法运行或打包的问题。 */
    private List<String> errors;

    /** 针对错误和代码质量给出的改进建议。 */
    private List<String> suggestions;
}
