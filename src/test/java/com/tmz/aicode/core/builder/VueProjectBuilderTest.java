package com.tmz.aicode.core.builder;

import com.tmz.aicode.model.dto.build.BuildProgress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 验证构建前的目录检查。测试不会运行 npm，也不会访问模型或网络。
 */
class VueProjectBuilderTest {

    @TempDir
    Path tempDir;

    /**
     * 不存在的项目目录应当直接返回失败，避免启动没有意义的外部进程。
     */
    @Test
    void rejectsMissingProjectDirectory() {
        Path missingProject = tempDir.resolve("missing-project");
        List<BuildProgress> progressEvents = new ArrayList<>();

        boolean result = new VueProjectBuilder().buildProject(
                missingProject.toString(), progressEvents::add);

        assertFalse(result);
        assertEquals(2, progressEvents.size());
        assertEquals(BuildProgress.EVENT_BUILD_START, progressEvents.getFirst().getEvent());
        assertEquals(BuildProgress.STATUS_FAILED, progressEvents.getLast().getStatus());
        assertEquals("project_check", progressEvents.getLast().getStage());
    }

    /**
     * 缺少 package.json 时无法确定依赖和构建脚本，应当在执行 npm 前停止。
     */
    @Test
    void rejectsProjectWithoutPackageJson() throws Exception {
        Path projectDir = Files.createDirectory(tempDir.resolve("vue-project"));

        boolean result = new VueProjectBuilder().buildProject(projectDir.toString());

        assertFalse(result);
    }
}
