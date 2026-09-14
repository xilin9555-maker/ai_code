package com.tmz.aicode.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.ZipUtil;
import com.tmz.aicode.exception.BusinessException;
import com.tmz.aicode.exception.ErrorCode;
import com.tmz.aicode.exception.ThrowUtils;
import com.tmz.aicode.service.ProjectDownloadService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileFilter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 项目代码下载服务实现。
 *
 * 压缩过程直接写入响应输出流，不需要先在服务器上生成一个长期保存的 ZIP 文件。这样可以
 * 减少磁盘占用，也避免定期清理下载压缩包。过滤器会检查路径中的每一级目录，进入被忽略
 * 的目录前就停止处理，因此依赖和构建产物不会出现在下载结果中。
 */
@Service
@Slf4j
public class ProjectDownloadServiceImpl implements ProjectDownloadService {

    /**
     * 不需要交付给用户的目录和文件。
     *
     * 依赖目录和构建产物可以重新生成；编辑器配置、版本库信息及环境文件可能体积很大或
     * 包含本机信息，因此统一从下载包中排除。名称统一保存为小写，匹配时不区分大小写。
     */
    private static final Set<String> IGNORED_NAMES = Set.of(
            "node_modules",
            ".git",
            "dist",
            "build",
            ".ds_store",
            ".env",
            "target",
            ".mvn",
            ".idea",
            ".vscode"
    );

    /**
     * 不需要包含在下载包中的临时文件扩展名。
     */
    private static final Set<String> IGNORED_EXTENSIONS = Set.of(
            ".log",
            ".tmp",
            ".cache"
    );

    /**
     * 下载文件名会进入 Content-Disposition 响应头，只允许安全的英文字符、数字和常用符号。
     */
    private static final Pattern SAFE_FILE_NAME = Pattern.compile("[A-Za-z0-9._-]+");

    /**
     * 将指定目录过滤后压缩，并把 ZIP 数据写入浏览器响应。
     *
     * @param projectPath      需要压缩的项目根目录
     * @param downloadFileName 下载文件名，不包含后缀
     * @param response         当前 HTTP 响应
     */
    @Override
    public void downloadProjectAsZip(String projectPath,
                                     String downloadFileName,
                                     HttpServletResponse response) {
        ThrowUtils.throwIf(StrUtil.isBlank(projectPath),
                ErrorCode.PARAMS_ERROR, "项目路径不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(downloadFileName),
                ErrorCode.PARAMS_ERROR, "下载文件名不能为空");
        ThrowUtils.throwIf(!SAFE_FILE_NAME.matcher(downloadFileName).matches(),
                ErrorCode.PARAMS_ERROR, "下载文件名包含不支持的字符");
        ThrowUtils.throwIf(response == null,
                ErrorCode.PARAMS_ERROR, "HTTP 响应不能为空");

        File projectDir = new File(projectPath);
        ThrowUtils.throwIf(!projectDir.exists(),
                ErrorCode.NOT_FOUND_ERROR, "项目目录不存在");
        ThrowUtils.throwIf(!projectDir.isDirectory(),
                ErrorCode.PARAMS_ERROR, "指定路径不是目录");

        Path projectRoot = projectDir.toPath().toAbsolutePath().normalize();
        log.info("开始打包项目代码，项目目录：{}，下载文件名：{}.zip",
                projectRoot, downloadFileName);

        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/zip");
        response.setHeader(
                HttpHeaders.CONTENT_DISPOSITION,
                String.format("attachment; filename=\"%s.zip\"", downloadFileName)
        );

        // Hutool 会在遍历每个文件和目录时调用过滤器，被拒绝的目录不会继续向下加入压缩包。
        FileFilter filter = file -> isPathAllowed(projectRoot, file.toPath());
        try {
            // false 表示 ZIP 内直接放置项目内容，不额外套一层服务器上的项目目录名称。
            ZipUtil.zip(
                    response.getOutputStream(),
                    StandardCharsets.UTF_8,
                    false,
                    filter,
                    projectDir
            );
            log.info("项目代码打包完成，下载文件名：{}.zip", downloadFileName);
        } catch (Exception e) {
            log.error("项目代码打包失败，项目目录：{}", projectRoot, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "项目代码下载失败");
        }
    }

    /**
     * 检查一个路径能否进入压缩包。
     *
     * 路径中的每一部分都会参与检查，例如 {@code src/demo/node_modules/a.js} 即使依赖目录
     * 位于项目深处，也会被完整排除。符号链接同样不允许进入压缩包，避免链接指向项目外部
     * 时把服务器上的其他文件一并下载。
     *
     * @param projectRoot 已规范化的项目根目录
     * @param fullPath    当前准备加入压缩包的文件或目录
     * @return 路径安全且不在忽略列表中时返回 {@code true}
     */
    private boolean isPathAllowed(Path projectRoot, Path fullPath) {
        Path normalizedPath = fullPath.toAbsolutePath().normalize();
        if (!normalizedPath.startsWith(projectRoot) || Files.isSymbolicLink(normalizedPath)) {
            return false;
        }

        Path relativePath = projectRoot.relativize(normalizedPath);
        for (Path part : relativePath) {
            String partName = part.toString().toLowerCase(Locale.ROOT);
            if (IGNORED_NAMES.contains(partName)) {
                return false;
            }
            if (IGNORED_EXTENSIONS.stream().anyMatch(partName::endsWith)) {
                return false;
            }
        }
        return true;
    }
}
