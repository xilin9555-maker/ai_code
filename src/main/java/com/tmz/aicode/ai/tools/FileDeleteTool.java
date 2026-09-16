package com.tmz.aicode.ai.tools;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/**
 * 允许 AI 删除当前 Vue 工程中明确指定的单个普通文件。
 *
 * 工具不删除目录，并保护入口、依赖清单、构建和 TypeScript 配置等关键文件。若确实
 * 需要调整这些文件，应使用写入或修改工具，而不是先删除再创建。
 */
@Slf4j
@Component
public class FileDeleteTool extends BaseTool {

    private static final Set<String> IMPORTANT_FILE_NAMES = Set.of(
            "package.json", "package-lock.json", "yarn.lock", "pnpm-lock.yaml",
            "vite.config.js", "vite.config.ts", "vue.config.js",
            "tsconfig.json", "tsconfig.app.json", "tsconfig.node.json",
            "index.html", "main.js", "main.ts", "app.vue", ".gitignore", "readme.md"
    );

    /**
     * 删除一个非关键的现有文件。
     *
     * @param relativeFilePath 相对于 Vue 工程根目录的文件路径
     * @param appId LangChain4j 从当前会话 memoryId 注入的应用 id
     * @return 删除结果；路径不存在时明确告知无需重复删除
     */
    @Tool("删除当前 Vue 工程内一个不再需要的非关键文件；不能删除目录或项目入口配置")
    public String deleteFile(
            @P("文件相对于 Vue 工程根目录的路径，例如 src/components/UnusedCard.vue")
            String relativeFilePath,
            @ToolMemoryId Long appId) {
        final Path targetPath;
        try {
            targetPath = ProjectFileToolSupport.resolveFile(appId, relativeFilePath);
        } catch (IllegalArgumentException e) {
            return "文件删除失败：" + e.getMessage();
        }

        if (!Files.exists(targetPath, LinkOption.NOFOLLOW_LINKS)) {
            return "文件无需删除：指定路径不存在";
        }
        if (!Files.isRegularFile(targetPath, LinkOption.NOFOLLOW_LINKS)) {
            return "文件删除失败：指定路径不是普通文件，目录不能通过本工具删除";
        }

        String lowerFileName = targetPath.getFileName().toString().toLowerCase(Locale.ROOT);
        if (isImportantFile(lowerFileName)) {
            return "文件删除失败：不允许删除项目关键文件 " + targetPath.getFileName();
        }

        try {
            Files.delete(targetPath);
            String displayPath = ProjectFileToolSupport.toDisplayPath(appId, targetPath);
            log.info("工程文件删除成功，应用 id：{}，路径：{}", appId, targetPath);
            return "文件删除成功：" + displayPath;
        } catch (IOException | SecurityException e) {
            log.error("工程文件删除失败，应用 id：{}，相对路径：{}", appId, relativeFilePath, e);
            return "文件删除失败：服务器未能删除该文件";
        }
    }

    private boolean isImportantFile(String lowerFileName) {
        return IMPORTANT_FILE_NAMES.contains(lowerFileName) || lowerFileName.startsWith(".env");
    }

    /** 使用工具方法名作为流式事件中的稳定标识。 */
    @Override
    public String getToolName() {
        return "deleteFile";
    }

    /** 返回适合直接展示给用户的工具名称。 */
    @Override
    public String getDisplayName() {
        return "删除文件";
    }

    /** 删除操作只需展示目标路径，不重复输出文件内容。 */
    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        String relativeFilePath = StrUtil.blankToDefault(
                arguments.getStr("relativeFilePath"), "未提供路径");
        return String.format("[工具调用] %s %s", getDisplayName(), relativeFilePath);
    }
}
