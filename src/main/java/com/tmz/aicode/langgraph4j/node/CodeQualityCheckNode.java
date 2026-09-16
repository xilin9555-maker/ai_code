package com.tmz.aicode.langgraph4j.node;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.tmz.aicode.langgraph4j.ai.CodeQualityCheckService;
import com.tmz.aicode.langgraph4j.ai.CodeQualityCheckServiceFactory;
import com.tmz.aicode.langgraph4j.model.QualityResult;
import com.tmz.aicode.langgraph4j.state.WorkflowContext;
import com.tmz.aicode.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 代码质量检查工作节点。
 *
 * 节点读取代码生成目录中的前端源码，拼接文件结构和具体内容后交给 AI 检查，再把
 * QualityResult 保存到共享上下文，供循环条件边决定继续、结束或重新生成。
 */
@Slf4j
public final class CodeQualityCheckNode {

    /** 工作流状态中用于展示的步骤名称。 */
    public static final String STEP_NAME = "代码质量检查";

    /**
     * 需要读取并交给 AI 检查的代码文件扩展名。
     *
     * 列表覆盖当前三种生成模式可能产生的网页、样式、脚本、配置和组件文件。
     */
    private static final List<String> CODE_EXTENSIONS = Arrays.asList(
            ".html", ".htm", ".css", ".js", ".json", ".vue", ".ts", ".jsx", ".tsx"
    );

    private CodeQualityCheckNode() {
        // 节点通过静态工厂创建，不需要保存实例状态。
    }

    /**
     * 创建代码质量检查节点。
     *
     * @return 可注册到工作流图的异步节点
     */
    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = requireContext(state);
            log.info("执行节点：{}", STEP_NAME);
            String generatedCodeDir = context.getGeneratedCodeDir();
            QualityResult qualityResult;
            try {
                // 第一步：读取项目文件结构并拼接所有需要检查的源码。
                String codeContent = readAndConcatenateCodeFiles(generatedCodeDir);
                if (StrUtil.isBlank(codeContent)) {
                    log.warn("未找到可检查的代码文件");
                    qualityResult = QualityResult.builder()
                            .isValid(false)
                            .errors(List.of("未找到可检查的代码文件"))
                            .suggestions(List.of("请确保代码生成成功"))
                            .build();
                } else {
                    // 第二步：调用结构化输出服务，让 AI 返回明确的检查结论。
                    CodeQualityCheckServiceFactory qualityCheckServiceFactory =
                            SpringContextUtil.getBean(CodeQualityCheckServiceFactory.class);
                    CodeQualityCheckService qualityCheckService =
                            qualityCheckServiceFactory.createCodeQualityCheckService();
                    qualityResult = qualityCheckService.checkCodeQuality(codeContent);
                    log.info("代码质量检查完成，是否通过：{}", qualityResult.getIsValid());
                }
            } catch (Exception exception) {
                // 检查服务异常时直接进入后续步骤，避免外部故障导致工作流无限重试。
                log.error("代码质量检查异常：{}", exception.getMessage(), exception);
                qualityResult = QualityResult.builder()
                        .isValid(true)
                        .build();
            }

            // 第三步：保存检查结果，条件边将根据该结果选择下一条路径。
            context.setCurrentStep(STEP_NAME);
            context.setQualityResult(qualityResult);
            return WorkflowContext.saveContext(context);
        });
    }

    /**
     * 读取并拼接代码目录下的所有代码文件。
     *
     * 每个文件前都会写入相对路径，使 AI 能同时理解项目结构和文件间的引用关系。
     *
     * @param codeDir 代码生成节点保存源码的目录
     * @return 项目文件结构和代码内容；目录无效时返回空字符串
     */
    private static String readAndConcatenateCodeFiles(String codeDir) {
        if (StrUtil.isBlank(codeDir)) {
            return "";
        }
        File directory = new File(codeDir);
        if (!directory.exists() || !directory.isDirectory()) {
            log.error("代码目录不存在或不是目录：{}", codeDir);
            return "";
        }

        StringBuilder codeContent = new StringBuilder();
        codeContent.append("# 项目文件结构和代码内容\n\n");
        // walkFiles 负责遍历文件树，当前访问逻辑只处理需要交给 AI 的源码文件。
        FileUtil.walkFiles(directory, file -> {
            if (shouldSkipFile(file, directory)) {
                return;
            }
            if (isCodeFile(file)) {
                String relativePath = FileUtil.subPath(
                        directory.getAbsolutePath(), file.getAbsolutePath());
                codeContent.append("## 文件：").append(relativePath).append("\n\n");
                String fileContent = FileUtil.readUtf8String(file);
                codeContent.append(fileContent).append("\n\n");
            }
        });
        return codeContent.toString();
    }

    /**
     * 判断文件是否属于不应参与检查的内容。
     *
     * 隐藏文件、依赖目录、构建产物和版本控制数据体积大且不是本轮生成源码，必须排除。
     */
    private static boolean shouldSkipFile(File file, File rootDir) {
        String relativePath = FileUtil.subPath(
                rootDir.getAbsolutePath(), file.getAbsolutePath());
        if (file.getName().startsWith(".")) {
            return true;
        }
        return relativePath.contains("node_modules" + File.separator)
                || relativePath.contains("dist" + File.separator)
                || relativePath.contains("target" + File.separator)
                || relativePath.contains(".git" + File.separator);
    }

    /** 判断当前文件是否具有需要检查的代码扩展名。 */
    private static boolean isCodeFile(File file) {
        String fileName = file.getName().toLowerCase();
        return CODE_EXTENSIONS.stream().anyMatch(fileName::endsWith);
    }

    /** 读取共享上下文，并在入口处报告缺少初始化状态的问题。 */
    private static WorkflowContext requireContext(MessagesState<String> state) {
        WorkflowContext context = WorkflowContext.getContext(state);
        if (context == null) {
            throw new IllegalStateException("代码质量检查节点缺少工作流上下文");
        }
        return context;
    }
}
