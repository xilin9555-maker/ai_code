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
import java.nio.file.StandardOpenOption;

/**
 * 允许 AI 对当前 Vue 工程中的文件进行局部、精确替换。
 *
 * 旧内容必须在文件中只出现一次。出现零次时不会写文件；出现多次时也会拒绝修改，
 * 并要求模型携带更多上下文重新调用，避免一次误改多个相似组件。
 */
@Slf4j
@Component
public class FileModifyTool extends BaseTool {

    /**
     * 使用新内容替换文件中唯一匹配的旧内容。
     *
     * @param relativeFilePath 相对于 Vue 工程根目录的文件路径
     * @param oldContent 文件中当前存在、且应当唯一的完整片段
     * @param newContent 用于替换旧片段的新内容，可以为空字符串
     * @param appId LangChain4j 从当前会话 memoryId 注入的应用 id
     * @return 修改结果或可供模型调整参数的明确原因
     */
    @Tool("局部修改当前 Vue 工程的已有文本文件；修改组件文字、样式、结构或逻辑时优先使用，仅当旧内容唯一匹配时执行")
    public String modifyFile(
            @P("文件相对于 Vue 工程根目录的路径，例如 src/pages/HomePage.vue")
            String relativeFilePath,
            @P("要替换的最小完整旧片段；提供刚好足以唯一定位的上下文，不要传入整个文件")
            String oldContent,
            @P("仅用于替换旧片段的新内容；不要传入未修改的其他文件内容，删除旧片段时可传空字符串")
            String newContent,
            @ToolMemoryId Long appId) {
        if (oldContent == null || oldContent.isEmpty()) {
            return "文件修改失败：要替换的旧内容不能为空";
        }
        if (newContent == null) {
            return "文件修改失败：替换后的新内容不能为 null";
        }

        final Path targetPath;
        try {
            targetPath = ProjectFileToolSupport.resolveFile(appId, relativeFilePath);
        } catch (IllegalArgumentException e) {
            return "文件修改失败：" + e.getMessage();
        }
        if (!Files.isRegularFile(targetPath, LinkOption.NOFOLLOW_LINKS)) {
            return "文件修改失败：文件不存在或不是普通文件";
        }

        try {
            if (ProjectFileToolSupport.isTextFileTooLarge(targetPath)) {
                return "文件修改失败：文件超过 1 MB，不适合进行文本替换";
            }
            String originalContent = Files.readString(targetPath, StandardCharsets.UTF_8);
            if (originalContent.indexOf('\0') >= 0) {
                return "文件修改失败：不支持修改二进制文件";
            }

            int matchCount = countOccurrences(originalContent, oldContent);
            if (matchCount == 0) {
                return "文件未修改：没有找到要替换的旧内容，请先重新读取文件";
            }
            if (matchCount > 1) {
                return "文件未修改：旧内容匹配到 " + matchCount
                        + " 处，请增加上下文使其唯一后重试";
            }

            int matchIndex = originalContent.indexOf(oldContent);
            String modifiedContent = originalContent.substring(0, matchIndex)
                    + newContent
                    + originalContent.substring(matchIndex + oldContent.length());
            if (originalContent.equals(modifiedContent)) {
                return "文件未修改：替换后的内容与原内容相同";
            }

            Files.writeString(
                    targetPath,
                    modifiedContent,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
            String displayPath = ProjectFileToolSupport.toDisplayPath(appId, targetPath);
            log.info("工程文件修改成功，应用 id：{}，路径：{}", appId, targetPath);
            return "文件修改成功：" + displayPath;
        } catch (IOException | SecurityException e) {
            log.error("工程文件修改失败，应用 id：{}，相对路径：{}", appId, relativeFilePath, e);
            return "文件修改失败：服务器未能更新该文件";
        }
    }

    /** 使用非重叠匹配计数，避免 {@link String#split(String)} 的正则语义。 */
    private int countOccurrences(String content, String target) {
        int count = 0;
        int searchFrom = 0;
        while ((searchFrom = content.indexOf(target, searchFrom)) >= 0) {
            count++;
            searchFrom += target.length();
        }
        return count;
    }

    /** 使用工具方法名作为流式事件中的稳定标识。 */
    @Override
    public String getToolName() {
        return "modifyFile";
    }

    /** 返回适合直接展示给用户的工具名称。 */
    @Override
    public String getDisplayName() {
        return "修改文件";
    }

    /**
     * 展示目标文件以及替换前后的内容，便于用户准确核对本次局部修改。
     */
    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        String relativeFilePath = StrUtil.blankToDefault(
                arguments.getStr("relativeFilePath"), "未提供路径");
        String oldContent = StrUtil.nullToEmpty(arguments.getStr("oldContent"));
        String newContent = StrUtil.nullToEmpty(arguments.getStr("newContent"));
        return String.format("""
                [工具调用] %s %s

                替换前：
                ```
                %s
                ```

                替换后：
                ```
                %s
                ```""", getDisplayName(), relativeFilePath, oldContent, newContent);
    }
}
