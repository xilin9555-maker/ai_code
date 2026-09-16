package com.tmz.aicode.langgraph4j.ai;

import com.tmz.aicode.langgraph4j.model.QualityResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 代码质量检查 AI 服务。
 *
 * LangChain4j 根据该接口创建运行时代理，并把模型返回的 JSON 转换为 QualityResult，
 * 使工作节点和条件边可以直接使用结构化结果，而不需要自行解析自然语言。
 */
public interface CodeQualityCheckService {

    /**
     * 分析生成代码并返回质量检查结果。
     *
     * @param codeContent 项目文件结构以及需要检查的全部代码内容
     * @return 包含是否通过、错误列表和改进建议的结构化结果
     */
    @SystemMessage(fromResource = "prompt/code-quality-check-system-prompt.txt")
    QualityResult checkCodeQuality(@UserMessage String codeContent);
}
