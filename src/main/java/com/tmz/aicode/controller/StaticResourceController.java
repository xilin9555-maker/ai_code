package com.tmz.aicode.controller;

import com.tmz.aicode.constant.AppConstant;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 提供 AI 生成网站的本地预览资源。
 *
 * 生成过程写入 code_output 工作目录，这个接口把目录中的 HTML、CSS、JavaScript 和图片
 * 作为静态资源返回。正式部署目录由 Nginx 提供服务，两种访问用途互不干扰。
 */
@RestController
@RequestMapping("/static")
public class StaticResourceController {

    /**
     * 生成目录名称只允许使用字母、数字、下划线和短横线，避免把路径片段解释成上级目录。
     */
    private static final Pattern DIRECTORY_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_-]+");

    private static final Path PREVIEW_ROOT_PATH =
            Path.of(AppConstant.CODE_OUTPUT_ROOT_DIR).toAbsolutePath().normalize();

    /**
     * 返回指定生成目录中的静态文件。
     *
     * 访问目录本身时先重定向到带斜杠的地址，使页面内的相对路径能够正确解析；访问根目录
     * 时默认读取 index.html。最终文件路径必须仍位于当前应用目录内，不能跨目录读取文件。
     *
     * @param directoryName 生成目录名，例如 multi_file_123456
     * @param request 当前请求，用于取得通配符匹配到的文件路径
     * @return 找到的静态文件、目录重定向或 404 响应
     */
    @GetMapping("/{directoryName}/**")
    public ResponseEntity<Resource> serveStaticResource(
            @PathVariable String directoryName,
            HttpServletRequest request) {
        if (!DIRECTORY_NAME_PATTERN.matcher(directoryName).matches()) {
            return ResponseEntity.notFound().build();
        }

        String mappingPath = (String) request.getAttribute(
                HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE
        );
        String directoryPrefix = "/static/" + directoryName;
        if (mappingPath == null || !mappingPath.startsWith(directoryPrefix)) {
            return ResponseEntity.notFound().build();
        }

        String resourcePath = mappingPath.substring(directoryPrefix.length());
        if (resourcePath.isEmpty()) {
            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(URI.create(request.getRequestURI() + "/"));
            return new ResponseEntity<>(headers, HttpStatus.MOVED_PERMANENTLY);
        }
        if ("/".equals(resourcePath)) {
            resourcePath = "/index.html";
        }

        Path appDirectory = PREVIEW_ROOT_PATH.resolve(directoryName).normalize();
        Path resourceFile = appDirectory.resolve(resourcePath.substring(1)).normalize();
        if (!appDirectory.startsWith(PREVIEW_ROOT_PATH)
                || !resourceFile.startsWith(appDirectory)
                || !Files.isRegularFile(resourceFile)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(resourceFile);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, getContentTypeWithCharset(resourceFile))
                .body(resource);
    }

    /**
     * 根据扩展名返回浏览器能够正确处理的内容类型，文本文件统一声明 UTF-8 编码。
     */
    private String getContentTypeWithCharset(Path filePath) {
        String name = filePath.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".html")) {
            return "text/html; charset=UTF-8";
        }
        if (name.endsWith(".css")) {
            return "text/css; charset=UTF-8";
        }
        if (name.endsWith(".js")) {
            return "application/javascript; charset=UTF-8";
        }
        if (name.endsWith(".json")) {
            return "application/json; charset=UTF-8";
        }
        try {
            String detectedType = Files.probeContentType(filePath);
            return detectedType == null ? "application/octet-stream" : detectedType;
        } catch (Exception ignored) {
            return "application/octet-stream";
        }
    }
}
