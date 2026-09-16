package com.tmz.aicode.ai.tools;

import com.tmz.aicode.constant.AppConstant;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * Vue 工程文件工具共用的路径解析与安全校验。
 *
 * 模型只能提交相对于 {@code vue_project_{appId}} 的路径。该类会统一拒绝绝对路径、
 * 上级目录越界以及已有路径中的符号链接，避免读取、覆盖或删除当前应用之外的文件。
 * 它不是暴露给模型的工具，因此不包含任何 {@code @Tool} 方法。
 */
final class ProjectFileToolSupport {

    /** 单次允许读取或修改的最大文本文件大小，防止把大文件整体送入模型上下文。 */
    static final long MAX_TEXT_FILE_SIZE_BYTES = 1024L * 1024L;

    private static final String PROJECT_DIR_PREFIX = "vue_project_";

    private ProjectFileToolSupport() {
        // 工具类不需要实例化。
    }

    /**
     * 解析项目内的文件路径。工程根目录本身不能作为文件目标。
     *
     * @param appId 当前工具调用所属的应用 id
     * @param relativeFilePath 模型提供的项目内相对路径
     * @return 经过标准化和边界检查的绝对路径
     * @throws IllegalArgumentException 参数无效、路径越界或路径包含符号链接
     */
    static Path resolveFile(Long appId, String relativeFilePath) {
        if (relativeFilePath == null || relativeFilePath.isBlank()) {
            throw new IllegalArgumentException("相对路径不能为空");
        }
        return resolvePath(appId, relativeFilePath, false);
    }

    /**
     * 解析项目内目录。空字符串表示当前应用的 Vue 工程根目录。
     *
     * @param appId 当前工具调用所属的应用 id
     * @param relativeDirectoryPath 相对于工程根目录的目录路径
     * @return 经过标准化和边界检查的绝对路径
     * @throws IllegalArgumentException 参数无效、路径越界或路径包含符号链接
     */
    static Path resolveDirectory(Long appId, String relativeDirectoryPath) {
        return resolvePath(appId, relativeDirectoryPath == null ? "" : relativeDirectoryPath, true);
    }

    /**
     * 将已经校验的目标路径转换为不暴露服务器目录的项目相对路径。
     */
    static String toDisplayPath(Long appId, Path targetPath) {
        return getProjectRoot(appId)
                .relativize(targetPath.toAbsolutePath().normalize())
                .toString()
                .replace('\\', '/');
    }

    /** 判断文件是否超过工具允许处理的文本大小。 */
    static boolean isTextFileTooLarge(Path path) throws java.io.IOException {
        return Files.size(path) > MAX_TEXT_FILE_SIZE_BYTES;
    }

    private static Path resolvePath(Long appId, String relativePath, boolean allowProjectRoot) {
        Path projectRoot = getProjectRoot(appId);
        final Path inputPath;
        try {
            inputPath = Path.of(relativePath).normalize();
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException("相对路径格式无效", e);
        }

        if (inputPath.isAbsolute()) {
            throw new IllegalArgumentException("只能使用项目内的相对路径");
        }

        Path targetPath = projectRoot.resolve(inputPath).normalize();
        if (!targetPath.startsWith(projectRoot) || (!allowProjectRoot && targetPath.equals(projectRoot))) {
            throw new IllegalArgumentException("文件路径不能超出当前项目目录");
        }

        rejectSymbolicLinks(projectRoot, targetPath);
        return targetPath;
    }

    private static Path getProjectRoot(Long appId) {
        if (appId == null || appId <= 0) {
            throw new IllegalArgumentException("应用 id 无效");
        }
        return Path.of(AppConstant.CODE_OUTPUT_ROOT_DIR)
                .toAbsolutePath()
                .normalize()
                .resolve(PROJECT_DIR_PREFIX + appId)
                .normalize();
    }

    /**
     * 不跟随工程内部的符号链接。即使链接表面位于项目目录内，它也可能指向系统上的任意
     * 位置；读取、修改或删除这种路径都会破坏项目边界假设。
     */
    private static void rejectSymbolicLinks(Path projectRoot, Path targetPath) {
        Path currentPath = projectRoot;
        for (Path segment : projectRoot.relativize(targetPath)) {
            currentPath = currentPath.resolve(segment);
            if (Files.isSymbolicLink(currentPath)) {
                throw new IllegalArgumentException("不允许访问项目中的符号链接");
            }
        }
    }
}
