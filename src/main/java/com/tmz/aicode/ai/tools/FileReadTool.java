package com.tmz.aicode.ai.tools;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

/**
 * 允许 AI 读取当前 Vue 工程中的单个文本文件。
 *
 * 工具只接受项目内相对路径，并限制单次读取大小。返回值只包含文件正文或简短错误，
 * 不会泄露服务器上的绝对目录。
 */
@Slf4j
@Component
public class FileReadTool extends BaseTool {

    /**
     * 读取一个现有源代码或配置文件。
     *
     * @param relativeFilePath 相对于 Vue 工程根目录的文件路径
     * @param appId LangChain4j 从当前会话 memoryId 注入的应用 id
     * @return UTF-8 文件正文；失败时返回可供模型纠正参数的错误信息
     */
    @Tool("读取当前 Vue 工程内指定文本文件的完整内容；路径必须是项目内的相对路径")
    public String readFile(
            @P("文件相对于 Vue 工程根目录的路径，例如 src/pages/HomePage.vue")
            String relativeFilePath,
            @ToolMemoryId Long appId) {
        final Path targetPath;
        try {
            targetPath = ProjectFileToolSupport.resolveFile(appId, relativeFilePath);
        } catch (IllegalArgumentException e) {
            return "文件读取失败：" + e.getMessage();
        }

        if (!Files.isRegularFile(targetPath, LinkOption.NOFOLLOW_LINKS)) {
            return "文件读取失败：文件不存在或不是普通文件";
        }

        try {
            if (ProjectFileToolSupport.isTextFileTooLarge(targetPath)) {
                return "文件读取失败：文件超过 1 MB，请读取更小的源代码文件";
            }
            String content = Files.readString(targetPath, StandardCharsets.UTF_8);
            if (content.indexOf('\0') >= 0) {
                return "文件读取失败：不支持读取二进制文件";
            }
            return content;
        } catch (IOException | SecurityException e) {
            log.error("工程文件读取失败，应用 id：{}，相对路径：{}", appId, relativeFilePath, e);
            return "文件读取失败：服务器未能读取该文件";
        }
    }

    /** 使用工具方法名作为流式事件中的稳定标识。 */
    @Override
    public String getToolName() {
        return "readFile";
    }

    /** 返回适合直接展示给用户的工具名称。 */
    @Override
    public String getDisplayName() {
        return "读取文件";
    }

    /** 读取操作只展示目标文件，文件正文仍只返回给模型分析。 */
    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        String relativeFilePath = StrUtil.blankToDefault(
                arguments.getStr("relativeFilePath"), "未提供路径");
        return String.format("[工具调用] %s %s", getDisplayName(), relativeFilePath);
    }
}
