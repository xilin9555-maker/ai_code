package com.tmz.aicode.langgraph4j.node;

import com.tmz.aicode.constant.AppConstant;
import com.tmz.aicode.core.AiCodeGeneratorFacade;
import com.tmz.aicode.langgraph4j.model.QualityResult;
import com.tmz.aicode.langgraph4j.state.WorkflowContext;
import com.tmz.aicode.model.enums.CodeGenTypeEnum;
import com.tmz.aicode.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import reactor.core.publisher.Flux;

import java.io.File;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 网站代码生成工作节点。
 *
 * 节点使用增强后的提示词和路由结果调用代码生成门面。由于工作流的下一步需要读取完整的
 * 生成目录，这里会同步等待流式生成结束，再把源码目录写入共享上下文。
 */
@Slf4j
public final class CodeGeneratorNode {

    /** 工作流状态中用于展示的步骤名称。 */
    public static final String STEP_NAME = "代码生成";

    /** 单次代码流允许的最长执行时间。 */
    private static final Duration GENERATION_TIMEOUT = Duration.ofMinutes(10);

    private CodeGeneratorNode() {
        // 节点通过静态工厂创建，不需要保存实例状态。
    }

    /**
     * 创建代码生成节点。
     *
     * @return 可注册到工作流图的异步节点
     */
    public static AsyncNodeAction<MessagesState<String>> create() {
        return create(ignored -> {
        });
    }

    /**
     * 创建能够向外转发代码生成片段的节点。
     *
     * @param outputConsumer 工作流执行入口提供的输出接收器
     * @return 可注册到工作流图的异步节点
     */
    public static AsyncNodeAction<MessagesState<String>> create(
            Consumer<String> outputConsumer) {
        Objects.requireNonNull(outputConsumer, "代码输出接收器不能为空");
        return node_async(state -> {
            WorkflowContext context = requireContext(state);
            log.info("执行节点：{}", STEP_NAME);

            // 构造用户消息，其中可能包含上一次质量检查发现的错误和修复建议。
            String userMessage = buildUserMessage(context);
            CodeGenTypeEnum generationType = context.getGenerationType();
            Long appId = context.getAppId();
            if (appId == null || appId <= 0) {
                throw new IllegalStateException("代码生成节点缺少有效的应用 id");
            }
            AiCodeGeneratorFacade codeGeneratorFacade =
                    SpringContextUtil.getBean(AiCodeGeneratorFacade.class);
            log.info("开始生成代码，类型：{}（{}）",
                    generationType.getValue(), generationType.getText());

            // 订阅代码流并等待完成，保证文件保存结束后再进入项目构建节点。
            Flux<String> codeStream = codeGeneratorFacade.generateAndSaveCodeStream(
                    userMessage, generationType, appId);
            codeStream
                    // 复用原有代码流，使聊天页能够继续展示模型文本和文件工具执行结果。
                    .doOnNext(outputConsumer)
                    .blockLast(GENERATION_TIMEOUT);

            String generatedCodeDir = String.format("%s%s%s_%s",
                    AppConstant.CODE_OUTPUT_ROOT_DIR,
                    File.separator,
                    generationType.getValue(),
                    appId);
            context.setCurrentStep(STEP_NAME);
            context.setGeneratedCodeDir(generatedCodeDir);
            log.info("代码生成完成，生成目录：{}", generatedCodeDir);
            return WorkflowContext.saveContext(context);
        });
    }

    /**
     * 构造发送给代码生成服务的用户消息。
     *
     * 首次执行时使用图片增强后的完整需求；质量检查失败后再次进入该节点时，则发送新的
     * 错误修复消息，让带有会话记忆的代码生成服务在现有项目基础上完成修改。
     */
    private static String buildUserMessage(WorkflowContext context) {
        String userMessage = context.getEnhancedPrompt();
        QualityResult qualityResult = context.getQualityResult();
        if (isQualityCheckFailed(qualityResult)) {
            userMessage = buildErrorFixPrompt(qualityResult);
        }
        return userMessage;
    }

    /**
     * 判断质量检查是否失败且包含可以用于修复的具体错误。
     *
     * @param qualityResult 上一次质量检查节点保存的结果
     * @return 检查明确失败并且错误列表非空时返回 true
     */
    private static boolean isQualityCheckFailed(QualityResult qualityResult) {
        return qualityResult != null
                && !qualityResult.getIsValid()
                && qualityResult.getErrors() != null
                && !qualityResult.getErrors().isEmpty();
    }

    /**
     * 根据错误列表和可选的改进建议构造修复消息。
     *
     * @param qualityResult 未通过的质量检查结果
     * @return 用于引导模型修改现有项目的用户消息
     */
    private static String buildErrorFixPrompt(QualityResult qualityResult) {
        StringBuilder errorInfo = new StringBuilder();
        errorInfo.append("\n\n## 上次生成的代码存在以下问题，请修复：\n");
        qualityResult.getErrors().forEach(error ->
                errorInfo.append("- ").append(error).append("\n"));
        if (qualityResult.getSuggestions() != null
                && !qualityResult.getSuggestions().isEmpty()) {
            errorInfo.append("\n## 修复建议：\n");
            qualityResult.getSuggestions().forEach(suggestion ->
                    errorInfo.append("- ").append(suggestion).append("\n"));
        }
        errorInfo.append("\n请根据上述问题和建议重新生成代码，确保修复所有提到的问题。");
        return errorInfo.toString();
    }

    /** 读取共享上下文，并在入口处报告缺少初始化状态的问题。 */
    private static WorkflowContext requireContext(MessagesState<String> state) {
        WorkflowContext context = WorkflowContext.getContext(state);
        if (context == null) {
            throw new IllegalStateException("代码生成节点缺少工作流上下文");
        }
        return context;
    }
}
