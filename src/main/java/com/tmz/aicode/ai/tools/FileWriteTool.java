package com.tmz.aicode.ai.tools;

import cn.hutool.core.util.StrUtil;
import com.tmz.aicode.constant.AppConstant;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * 允许 AI 通过工具调用创建或更新工程文件。
 *
 * 每个应用都写入 vue_project_{appId} 独立目录，避免两个应用生成同名文件时互相覆盖。
 * 工具只接受项目内的相对路径，并在写入前检查最终路径仍位于当前应用目录中，防止路径
 * 越界后覆盖服务器上的其他文件。
 */
@Slf4j
public class FileWriteTool {

    private static final String PROJECT_DIR_PREFIX = "vue_project_";

    /**
     * 把一份完整文件内容写入当前应用的 Vue 工程目录。
     *
     * 同一路径已经存在时会覆盖旧内容，这样 AI 在后续对话中可以直接修改已有文件。
     * 父目录不存在时会自动逐级创建。appId 由 LangChain4j 从当前会话的 memoryId 注入，
     * 不需要模型自行生成或传递，因而文件目录与当前应用始终保持一致。
     *
     * @param relativeFilePath 相对于 Vue 工程根目录的文件路径，例如 src/pages/HomePage.vue
     * @param content 要保存的完整文件内容
     * @param appId 当前会话对应的应用 id
     * @return 只包含相对路径的执行结果，避免向模型和前端暴露服务器目录
     */
    @Tool("将完整文件内容写入当前应用的 Vue 工程；路径必须是项目内的相对路径")
    public String writeFile(
            @P("文件相对于 Vue 工程根目录的路径，例如 src/components/AppCard.vue")
            String relativeFilePath,
            @P("要写入文件的完整内容；文件已存在时使用这份内容覆盖")
            String content,
            @ToolMemoryId Long appId) {
        if (appId == null || appId <= 0) {
            return "文件写入失败：应用 id 无效";
        }
        if (StrUtil.isBlank(relativeFilePath)) {
            return "文件写入失败：相对路径不能为空";
        }
        if (content == null) {
            return "文件写入失败：文件内容不能为空";
        }

        try {
            Path relativePath = Path.of(relativeFilePath).normalize();
            if (relativePath.isAbsolute()) {
                return "文件写入失败：只能使用项目内的相对路径";
            }

            Path projectRoot = Path.of(AppConstant.CODE_OUTPUT_ROOT_DIR)
                    .toAbsolutePath()
                    .normalize()
                    .resolve(PROJECT_DIR_PREFIX + appId)
                    .normalize();
            Path targetPath = projectRoot.resolve(relativePath).normalize();

            // normalize 会折叠路径中的“..”；最终路径不在项目目录内时必须拒绝写入。
            if (!targetPath.startsWith(projectRoot) || targetPath.equals(projectRoot)) {
                return "文件写入失败：文件路径不能超出当前项目目录";
            }

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

            String safeRelativePath = relativePath.toString().replace('\\', '/');
            log.info("工程文件写入成功，应用 id：{}，路径：{}", appId, targetPath);
            return "文件写入成功：" + safeRelativePath;
        } catch (InvalidPathException e) {
            log.warn("工程文件路径格式无效，应用 id：{}，相对路径：{}", appId, relativeFilePath, e);
            return "文件写入失败：相对路径格式无效";
        } catch (IOException | SecurityException e) {
            log.error("工程文件写入失败，应用 id：{}，相对路径：{}", appId, relativeFilePath, e);
            return "文件写入失败：服务器未能保存该文件";
        }
    }
}
