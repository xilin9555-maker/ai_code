package com.tmz.aicode.service.impl;

import com.tmz.aicode.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 项目下载服务的本地单元测试。
 *
 * 测试只在临时目录中创建少量文件，再读取内存中的 ZIP 响应，不会访问数据库或网络。
 */
class ProjectDownloadServiceImplTest {

    @TempDir
    Path tempDir;

    /**
     * 源代码应进入压缩包，依赖、构建产物、环境文件和日志应被过滤。
     */
    @Test
    void downloadsSourceFilesAndFiltersUnneededContent() throws IOException {
        Path projectDir = tempDir.resolve("vue_project_1001");
        writeFile(projectDir.resolve("package.json"), "{\"name\":\"demo\"}");
        writeFile(projectDir.resolve("src/main.js"), "console.log('ok')");
        writeFile(projectDir.resolve("node_modules/pkg/index.js"), "dependency");
        writeFile(projectDir.resolve("dist/assets/index.js"), "bundle");
        writeFile(projectDir.resolve(".env"), "TOKEN=secret");
        writeFile(projectDir.resolve("logs/run.log"), "debug");

        MockHttpServletResponse response = new MockHttpServletResponse();
        ProjectDownloadServiceImpl service = new ProjectDownloadServiceImpl();

        service.downloadProjectAsZip(projectDir.toString(), "1001", response);

        assertEquals("application/zip", response.getContentType());
        assertEquals("attachment; filename=\"1001.zip\"",
                response.getHeader("Content-Disposition"));
        Map<String, String> zipFiles = readZipFiles(response.getContentAsByteArray());
        assertEquals("{\"name\":\"demo\"}", zipFiles.get("package.json"));
        assertEquals("console.log('ok')", zipFiles.get("src/main.js"));
        assertFalse(zipFiles.keySet().stream().anyMatch(name -> name.contains("node_modules")));
        assertFalse(zipFiles.keySet().stream().anyMatch(name -> name.startsWith("dist/")));
        assertFalse(zipFiles.containsKey(".env"));
        assertFalse(zipFiles.keySet().stream().anyMatch(name -> name.endsWith(".log")));
    }

    /**
     * 下载文件名进入响应头，包含换行等特殊字符时必须在写响应前拒绝。
     */
    @Test
    void rejectsUnsafeDownloadFileName() throws IOException {
        Path projectDir = tempDir.resolve("safe_project");
        Files.createDirectories(projectDir);
        ProjectDownloadServiceImpl service = new ProjectDownloadServiceImpl();

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.downloadProjectAsZip(
                        projectDir.toString(),
                        "bad\r\nname",
                        new MockHttpServletResponse()
                )
        );

        assertEquals("下载文件名包含不支持的字符", exception.getMessage());
    }

    private void writeFile(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private Map<String, String> readZipFiles(byte[] zipBytes) throws IOException {
        Map<String, String> files = new HashMap<>();
        try (ZipInputStream zipInputStream = new ZipInputStream(
                new ByteArrayInputStream(zipBytes),
                StandardCharsets.UTF_8
        )) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    files.put(
                            entry.getName().replace('\\', '/'),
                            new String(zipInputStream.readAllBytes(), StandardCharsets.UTF_8)
                    );
                }
                zipInputStream.closeEntry();
            }
        }
        assertTrue(zipBytes.length > 0);
        return files;
    }
}
