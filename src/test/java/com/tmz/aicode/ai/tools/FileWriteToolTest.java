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
 * 验证文件工具的覆盖写入和目录边界保护。
 *
 * 测试只操作随机应用对应的本地临时工程目录，不会创建 AI 服务或发出模型请求。
 */
class FileWriteToolTest {

    private final FileWriteTool fileWriteTool = new FileWriteTool();

    /**
     * 嵌套目录应自动创建，再次写入同一路径时应完整替换旧内容。
     */
    @Test
    void shouldCreateDirectoriesAndOverwriteExistingFile() throws IOException {
        long appId = createTestAppId();
        Path projectRoot = getProjectRoot(appId);
        Path targetFile = projectRoot.resolve("src/components/AppCard.vue");

        try {
            String firstResult = fileWriteTool.writeFile(
                    "src/components/AppCard.vue",
                    "<template>first</template>",
                    appId
            );
            String secondResult = fileWriteTool.writeFile(
                    "src/components/AppCard.vue",
                    "<template>second</template>",
                    appId
            );

            assertEquals("文件写入成功：src/components/AppCard.vue", firstResult);
            assertEquals("文件写入成功：src/components/AppCard.vue", secondResult);
            assertEquals("<template>second</template>", Files.readString(targetFile));
        } finally {
            deleteDirectory(projectRoot);
        }
    }

    /**
     * 带有上级目录跳转的路径不得离开当前应用的工程根目录。
     */
    @Test
    void shouldRejectPathOutsideProjectDirectory() throws IOException {
        long appId = createTestAppId();
        Path projectRoot = getProjectRoot(appId);
        Path escapedFile = projectRoot.getParent().resolve("outside-" + appId + ".txt");

        try {
            String result = fileWriteTool.writeFile("../outside-" + appId + ".txt", "unsafe", appId);

            assertTrue(result.startsWith("文件写入失败"));
            assertFalse(Files.exists(escapedFile));
        } finally {
            deleteDirectory(projectRoot);
            Files.deleteIfExists(escapedFile);
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
