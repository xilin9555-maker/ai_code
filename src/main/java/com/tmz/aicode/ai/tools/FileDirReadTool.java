package com.tmz.aicode.ai.tools;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 允许 AI 递归查看当前 Vue 工程的目录结构。
 *
 * 遍历时会跳过依赖、构建产物、IDE 配置和环境变量文件，既减少无关 token，也避免
 * 将可能含有密钥的配置发送给模型。工具不会跟随符号链接，最多返回 500 个条目。
 */
@Slf4j
@Component
public class FileDirReadTool extends BaseTool {

    private static final int MAX_ENTRIES = 500;

    /** 不应进入模型上下文的目录和文件名，比较时忽略大小写。 */
    private static final Set<String> IGNORED_NAMES = Set.of(
            "node_modules", ".git", "dist", "build", "target", "coverage",
            ".mvn", ".idea", ".vscode", ".ds_store"
    );

    /** 通常属于日志、缓存或临时产物的文件后缀。 */
    private static final Set<String> IGNORED_EXTENSIONS = Set.of(
            ".log", ".tmp", ".cache", ".lock", ".class"
    );

    /**
     * 读取指定目录下的文件树。
     *
     * @param relativeDirPath 项目内目录；为空时读取整个 Vue 工程
     * @param appId LangChain4j 从当前会话 memoryId 注入的应用 id
     * @return 使用缩进和斜杠表示层级的目录结构
     */
    @Tool("递归读取当前 Vue 工程的目录结构；空路径表示项目根目录")
    public String readDir(
            @P("目录相对于 Vue 工程根目录的路径，例如 src；读取整个项目时传空字符串")
            String relativeDirPath,
            @ToolMemoryId Long appId) {
        final Path targetDirectory;
        try {
            targetDirectory = ProjectFileToolSupport.resolveDirectory(appId, relativeDirPath);
        } catch (IllegalArgumentException e) {
            return "目录读取失败：" + e.getMessage();
        }

        if (!Files.isDirectory(targetDirectory, LinkOption.NOFOLLOW_LINKS)) {
            return "目录读取失败：目录不存在或不是普通目录";
        }

        List<Path> entries = new ArrayList<>();
        boolean[] truncated = {false};
        try {
            Files.walkFileTree(targetDirectory, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attrs) {
                    if (!directory.equals(targetDirectory) && shouldIgnore(directory)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return addEntry(directory, targetDirectory, entries, truncated);
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (!attrs.isSymbolicLink() && !shouldIgnore(file)) {
                        return addEntry(file, targetDirectory, entries, truncated);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException | SecurityException e) {
            log.error("工程目录读取失败，应用 id：{}，相对目录：{}", appId,
                    relativeDirPath, e);
            return "目录读取失败：服务器未能遍历该目录";
        }

        entries.sort(Comparator.comparing(
                path -> normalizePath(targetDirectory.relativize(path)),
                String.CASE_INSENSITIVE_ORDER
        ));

        String displayDirectory = ProjectFileToolSupport.toDisplayPath(appId, targetDirectory);
        StringBuilder result = new StringBuilder("项目目录结构（")
                .append(displayDirectory.isEmpty() ? "/" : displayDirectory)
                .append("）：\n");
        for (Path entry : entries) {
            Path relativePath = targetDirectory.relativize(entry);
            int depth = Math.max(0, relativePath.getNameCount() - 1);
            result.append("  ".repeat(depth))
                    .append(entry.getFileName());
            if (Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) {
                result.append('/');
            }
            result.append('\n');
        }
        if (truncated[0]) {
            result.append("……目录条目超过 ").append(MAX_ENTRIES).append(" 个，结果已截断\n");
        }
        return result.toString().stripTrailing();
    }

    private FileVisitResult addEntry(Path entry,
                                     Path root,
                                     List<Path> entries,
                                     boolean[] truncated) {
        if (entry.equals(root)) {
            return FileVisitResult.CONTINUE;
        }
        if (entries.size() >= MAX_ENTRIES) {
            truncated[0] = true;
            return FileVisitResult.TERMINATE;
        }
        entries.add(entry);
        return FileVisitResult.CONTINUE;
    }

    private boolean shouldIgnore(Path path) {
        String lowerName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (IGNORED_NAMES.contains(lowerName) || lowerName.startsWith(".env")) {
            return true;
        }
        return IGNORED_EXTENSIONS.stream().anyMatch(lowerName::endsWith);
    }

    private String normalizePath(Path path) {
        return path.toString().replace('\\', '/');
    }

    /** 使用工具方法名作为流式事件中的稳定标识。 */
    @Override
    public String getToolName() {
        return "readDir";
    }

    /** 返回适合直接展示给用户的工具名称。 */
    @Override
    public String getDisplayName() {
        return "读取目录";
    }

    /** 空目录参数代表项目根目录，展示时转换成更自然的文字。 */
    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        String relativeDirPath = StrUtil.blankToDefault(
                arguments.getStr("relativeDirPath"), "根目录");
        return String.format("[工具调用] %s %s", getDisplayName(), relativeDirPath);
    }
}
