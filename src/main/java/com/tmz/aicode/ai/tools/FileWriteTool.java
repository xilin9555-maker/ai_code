package com.tmz.aicode.ai.tools;

import cn.hutool.core.io.FileUtil;
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
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * 允许 AI 通过工具调用创建或完整重写工程文件。
 *
 * 每个应用都写入 vue_project_{appId} 独立目录，避免两个应用生成同名文件时互相覆盖。
 * 工具只接受项目内的相对路径，并在写入前检查最终路径仍位于当前应用目录中，防止路径
 * 越界后覆盖服务器上的其他文件。
 */
@Slf4j
@Component
public class FileWriteTool extends BaseTool {

    /**
     * 把一份完整文件内容写入当前应用的 Vue 工程目录。
     *
     * 同一路径已经存在时仍支持完整覆盖，供确实需要整体重写的场景使用；已有组件的
     * 小范围修改应使用 FileModifyTool，避免向前端展示整份文件。父目录不存在时会自动
     * 逐级创建。appId 由 LangChain4j 从当前会话的 memoryId 注入，因而文件目录与当前
     * 应用始终保持一致。
     *
     * @param relativeFilePath 相对于 Vue 工程根目录的文件路径，例如 src/pages/HomePage.vue
     * @param content 要保存的完整文件内容
     * @param appId 当前会话对应的应用 id
     * @return 只包含相对路径的执行结果，避免向模型和前端暴露服务器目录
     */
    @Tool("创建新文件或在确有必要时完整重写文件；已有文件的局部修改必须改用 modifyFile")
    public String writeFile(
            @P("文件相对于 Vue 工程根目录的路径，例如 src/components/AppCard.vue")
            String relativeFilePath,
            @P("新文件或完整重写文件的全部内容；不要用它提交已有文件的局部修改")
            String content,
            @ToolMemoryId Long appId) {
        if (content == null) {
            return "文件写入失败：文件内容不能为空";
        }

        final Path targetPath;
        try {
            targetPath = ProjectFileToolSupport.resolveFile(appId, relativeFilePath);
        } catch (IllegalArgumentException e) {
            return "文件写入失败：" + e.getMessage();
        }

        try {
            Path parentDirectory = targetPath.getParent();
            if (parentDirectory != null) {
                Files.createDirectories(parentDirectory);
            }
            Files.writeString(
                    targetPath,
                    content,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );

            String safeRelativePath = ProjectFileToolSupport.toDisplayPath(appId, targetPath);
            log.info("工程文件写入成功，应用 id：{}，路径：{}", appId, targetPath);
            return "文件写入成功：" + safeRelativePath;
        } catch (IOException | SecurityException e) {
            log.error("工程文件写入失败，应用 id：{}，相对路径：{}", appId, relativeFilePath, e);
            return "文件写入失败：服务器未能保存该文件";
        }
    }

    /** 使用工具方法名作为流式事件中的稳定标识。 */
    @Override
    public String getToolName() {
        return "writeFile";
    }

    /** 返回适合直接展示给用户的工具名称。 */
    @Override
    public String getDisplayName() {
        return "写入文件";
    }

    /**
     * 展示写入路径和完整内容，并根据文件后缀设置 Markdown 代码语言。
     */
    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        String relativeFilePath = StrUtil.blankToDefault(
                arguments.getStr("relativeFilePath"), "未提供路径");
        String language = StrUtil.blankToDefault(FileUtil.getSuffix(relativeFilePath), "text");
        String content = StrUtil.nullToEmpty(arguments.getStr("content"));
        return String.format("""
                [工具调用] %s %s
                ```%s
                %s
                ```""", getDisplayName(), relativeFilePath, language, content);
    }
}
