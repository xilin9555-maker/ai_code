package com.tmz.aicode.langgraph4j.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.system.SystemUtil;
import com.tmz.aicode.exception.BusinessException;
import com.tmz.aicode.exception.ErrorCode;
import com.tmz.aicode.langgraph4j.model.ImageResource;
import com.tmz.aicode.langgraph4j.model.enums.ImageCategoryEnum;
import com.tmz.aicode.manager.CosManager;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 将 Mermaid 文本转换为架构图并上传到对象存储。
 *
 * 工具通过本机 Mermaid CLI 生成透明背景的 SVG，再复用 CosManager 上传文件并返回统一的
 * ImageResource。所有临时文件都会在处理结束后清理，避免长期运行时占用服务器磁盘。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MermaidDiagramTool {

    /** Mermaid CLI 单次转换允许的最长执行时间。 */
    private static final long CLI_TIMEOUT_SECONDS = 60;

    /** 命令执行失败时最多写入异常信息的字符数。 */
    private static final int MAX_ERROR_OUTPUT_LENGTH = 1_000;

    private final CosManager cosManager;

    /**
     * 根据 Mermaid 代码生成一张架构图。
     *
     * @param mermaidCode Mermaid 图表代码
     * @param description 架构图的用途或内容描述
     * @return 上传成功后的单张架构图资源；参数无效或处理失败时返回空列表
     */
    @Tool("将 Mermaid 代码转换为架构图图片，用于展示系统结构和技术关系")
    public List<ImageResource> generateMermaidDiagram(
            @P("完整的 Mermaid 图表代码") String mermaidCode,
            @P("架构图的用途和内容描述") String description) {
        if (StrUtil.isBlank(mermaidCode)) {
            log.warn("Mermaid 代码为空，跳过架构图生成");
            return List.of();
        }

        File diagramFile = null;
        try {
            diagramFile = convertMermaidToSvg(mermaidCode);
            String keyName = "/mermaid/%s/%s".formatted(
                    RandomUtil.randomString(5), diagramFile.getName());
            String cosUrl = cosManager.uploadFile(keyName, diagramFile);
            if (StrUtil.isBlank(cosUrl)) {
                log.error("架构图上传失败，对象存储未返回访问地址");
                return List.of();
            }

            String normalizedDescription = StrUtil.isBlank(description)
                    ? "系统架构图"
                    : description.trim();
            return List.of(ImageResource.builder()
                    .category(ImageCategoryEnum.ARCHITECTURE)
                    .description(normalizedDescription)
                    .url(cosUrl)
                    .build());
        } catch (Exception exception) {
            log.error("生成架构图失败：{}", exception.getMessage(), exception);
            return List.of();
        } finally {
            // SVG 只承担上传过程中的临时中转作用，上传结束后立即删除。
            if (diagramFile != null) {
                FileUtil.del(diagramFile);
            }
        }
    }

    /**
     * 调用本机 Mermaid CLI 将文本图表转换为 SVG。
     *
     * 命令参数通过 ProcessBuilder 分开传递，不依赖字符串拼接和路径引号。Windows 下通过
     * cmd.exe 执行 mmdc.cmd，其他系统直接执行 mmdc。
     *
     * @param mermaidCode Mermaid 图表代码
     * @return 已生成且非空的临时 SVG 文件，调用方负责删除
     */
    File convertMermaidToSvg(String mermaidCode) {
        File inputFile = FileUtil.createTempFile("mermaid_input_", ".mmd", true);
        File outputFile = FileUtil.createTempFile("mermaid_output_", ".svg", true);
        File commandLogFile = FileUtil.createTempFile("mermaid_command_", ".log", true);
        boolean conversionSucceeded = false;

        try {
            FileUtil.writeUtf8String(mermaidCode, inputFile);
            ProcessBuilder processBuilder = new ProcessBuilder(buildCommand(inputFile, outputFile));
            processBuilder.redirectErrorStream(true);
            processBuilder.redirectOutput(commandLogFile);

            Process process = processBuilder.start();
            boolean finished = process.waitFor(CLI_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new BusinessException(
                        ErrorCode.SYSTEM_ERROR, "Mermaid CLI 执行超时");
            }
            if (process.exitValue() != 0) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                        "Mermaid CLI 执行失败：" + readCommandError(commandLogFile));
            }
            if (!outputFile.isFile() || outputFile.length() == 0) {
                throw new BusinessException(
                        ErrorCode.SYSTEM_ERROR, "Mermaid CLI 未生成有效的 SVG 文件");
            }

            conversionSucceeded = true;
            return outputFile;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(
                    ErrorCode.SYSTEM_ERROR, "Mermaid CLI 执行被中断");
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "无法启动 Mermaid CLI，请确认已安装 mmdc");
        } finally {
            FileUtil.del(inputFile);
            FileUtil.del(commandLogFile);
            if (!conversionSucceeded) {
                FileUtil.del(outputFile);
            }
        }
    }

    /** 根据操作系统构造不会丢失带空格路径的命令参数。 */
    private List<String> buildCommand(File inputFile, File outputFile) {
        List<String> command = new ArrayList<>();
        if (SystemUtil.getOsInfo().isWindows()) {
            command.add("cmd.exe");
            command.add("/c");
            command.add("mmdc.cmd");
        } else {
            command.add("mmdc");
        }
        command.add("-i");
        command.add(inputFile.getAbsolutePath());
        command.add("-o");
        command.add(outputFile.getAbsolutePath());
        command.add("-b");
        command.add("transparent");
        return command;
    }

    /** 读取并截断命令输出，避免异常信息过长。 */
    private String readCommandError(File commandLogFile) {
        if (!commandLogFile.isFile()) {
            return "未获得命令输出";
        }
        String commandOutput = FileUtil.readUtf8String(commandLogFile).trim();
        if (StrUtil.isBlank(commandOutput)) {
            return "未获得命令输出";
        }
        if (commandOutput.length() <= MAX_ERROR_OUTPUT_LENGTH) {
            return commandOutput;
        }
        return commandOutput.substring(0, MAX_ERROR_OUTPUT_LENGTH) + "...";
    }
}
