package com.tmz.aicode.ai.tools;

import com.tmz.aicode.constant.AppConstant;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证工程增量修改工具的正常行为和关键安全边界。
 *
 * 每个测试使用独立的随机应用目录，并在结束后递归清理；测试不创建 AI 服务，也不会
 * 请求真实模型。
 */
class ProjectFileToolsTest {

    private final FileReadTool fileReadTool = new FileReadTool();
    private final FileDirReadTool fileDirReadTool = new FileDirReadTool();
    private final FileModifyTool fileModifyTool = new FileModifyTool();
    private final FileDeleteTool fileDeleteTool = new FileDeleteTool();

    @Test
    void shouldReadUtf8FileAndRejectPathTraversal() throws IOException {
        long appId = createTestAppId();
        Path projectRoot = getProjectRoot(appId);
        try {
            writeFile(projectRoot.resolve("src/App.vue"), "<template>你好</template>");

            assertEquals("<template>你好</template>", fileReadTool.readFile("src/App.vue", appId));
            assertTrue(fileReadTool.readFile("../outside.txt", appId)
                    .startsWith("文件读取失败：文件路径不能超出"));
        } finally {
            deleteDirectory(projectRoot);
        }
    }

    @Test
    void shouldListProjectTreeAndIgnoreDependenciesAndEnvironmentFiles() throws IOException {
        long appId = createTestAppId();
        Path projectRoot = getProjectRoot(appId);
        try {
            writeFile(projectRoot.resolve("src/pages/HomePage.vue"), "home");
            writeFile(projectRoot.resolve("src/main.js"), "main");
            writeFile(projectRoot.resolve("node_modules/pkg/index.js"), "dependency");
            writeFile(projectRoot.resolve("dist/index.html"), "build output");
            writeFile(projectRoot.resolve(".env.local"), "SECRET=value");

            String structure = fileDirReadTool.readDir("", appId);

            assertTrue(structure.contains("src/"));
            assertTrue(structure.contains("pages/"));
            assertTrue(structure.contains("HomePage.vue"));
            assertFalse(structure.contains("node_modules"));
            assertFalse(structure.contains("dist/"));
            assertFalse(structure.contains(".env.local"));
        } finally {
            deleteDirectory(projectRoot);
        }
    }

    @Test
    void shouldModifyOnlyUniqueTextAndRejectAmbiguousReplacement() throws IOException {
        long appId = createTestAppId();
        Path projectRoot = getProjectRoot(appId);
        Path targetFile = projectRoot.resolve("src/pages/HomePage.vue");
        try {
            writeFile(targetFile, "<h1>旧标题</h1>\n<p>重复</p>\n<p>重复</p>");

            String modified = fileModifyTool.modifyFile(
                    "src/pages/HomePage.vue", "<h1>旧标题</h1>", "<h1>新标题</h1>", appId
            );
            String ambiguous = fileModifyTool.modifyFile(
                    "src/pages/HomePage.vue", "重复", "已修改", appId
            );

            assertEquals("文件修改成功：src/pages/HomePage.vue", modified);
            assertTrue(ambiguous.contains("匹配到 2 处"));
            assertEquals("<h1>新标题</h1>\n<p>重复</p>\n<p>重复</p>", Files.readString(targetFile));
        } finally {
            deleteDirectory(projectRoot);
        }
    }

    @Test
    void shouldDeleteOrdinaryFileButProtectProjectEntry() throws IOException {
        long appId = createTestAppId();
        Path projectRoot = getProjectRoot(appId);
        Path removableFile = projectRoot.resolve("src/components/UnusedCard.vue");
        Path importantFile = projectRoot.resolve("package.json");
        try {
            writeFile(removableFile, "unused");
            writeFile(importantFile, "{}");

            String deleted = fileDeleteTool.deleteFile("src/components/UnusedCard.vue", appId);
            String protectedResult = fileDeleteTool.deleteFile("package.json", appId);

            assertEquals("文件删除成功：src/components/UnusedCard.vue", deleted);
            assertFalse(Files.exists(removableFile));
            assertTrue(protectedResult.startsWith("文件删除失败：不允许删除项目关键文件"));
            assertTrue(Files.exists(importantFile));
        } finally {
            deleteDirectory(projectRoot);
        }
    }

    private long createTestAppId() {
        return System.currentTimeMillis() * 1000 + Math.floorMod(System.nanoTime(), 1000);
    }

    private Path getProjectRoot(long appId) {
        return Path.of(AppConstant.CODE_OUTPUT_ROOT_DIR, "vue_project_" + appId)
                .toAbsolutePath()
                .normalize();
    }

    private void writeFile(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
