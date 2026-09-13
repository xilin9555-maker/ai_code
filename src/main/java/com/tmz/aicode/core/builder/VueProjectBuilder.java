package com.tmz.aicode.core.builder;

import cn.hutool.core.util.RuntimeUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;

/**
 * 负责把 AI 写出的 Vue 源码构建成浏览器可以直接访问的静态文件。
 *
 * Vue 源码中的单文件组件和模块导入不能直接交给浏览器执行，需要先安装依赖，再由
 * Vite 完成编译和打包。构建成功后生成的 dist 目录就是后续预览、部署所使用的成品。
 */
@Slf4j
@Component
public class VueProjectBuilder {

    private static final int INSTALL_TIMEOUT_SECONDS = 300;

    private static final int BUILD_TIMEOUT_SECONDS = 180;

    /**
     * 在虚拟线程中执行构建，让模型响应完成后可以及时结束当前请求。
     *
     * 构建属于耗时且主要等待磁盘、网络和子进程的任务，虚拟线程适合承载这类工作，
     * 同时不会长期占用 Web 请求线程。构建失败只记录日志，不会把已经完成的生成结果
     * 改成接口异常，用户仍然可以继续修改源码并再次尝试构建。
     *
     * @param projectPath Vue 项目根目录，目录内应当包含 package.json
     */
    public void buildProjectAsync(String projectPath) {
        Thread.ofVirtual()
                .name("vue-builder-" + System.currentTimeMillis())
                .start(() -> {
                    try {
                        buildProject(projectPath);
                    } catch (Exception e) {
                        log.error("异步构建 Vue 项目时发生异常，项目路径：{}", projectPath, e);
                    }
                });
    }

    /**
     * 依次安装项目依赖并执行生产构建。
     *
     * 每一步都会检查执行结果，任何一步失败都会立即停止，避免把不完整的 dist 目录
     * 误认为可以浏览的构建产物。最终还会确认 dist 确实是一个目录。
     *
     * @param projectPath Vue 项目根目录路径
     * @return package.json 存在、两条 npm 命令成功且 dist 目录已生成时返回 true
     */
    public boolean buildProject(String projectPath) {
        File projectDir = new File(projectPath);
        if (!projectDir.isDirectory()) {
            log.error("Vue 项目目录不存在：{}", projectDir.getAbsolutePath());
            return false;
        }

        File packageJson = new File(projectDir, "package.json");
        if (!packageJson.isFile()) {
            log.error("Vue 项目缺少 package.json：{}", packageJson.getAbsolutePath());
            return false;
        }

        log.info("开始构建 Vue 项目：{}", projectDir.getAbsolutePath());
        if (!executeNpmInstall(projectDir)) {
            log.error("Vue 项目依赖安装失败：{}", projectDir.getAbsolutePath());
            return false;
        }
        if (!executeNpmBuild(projectDir)) {
            log.error("Vue 项目打包失败：{}", projectDir.getAbsolutePath());
            return false;
        }

        File distDir = new File(projectDir, "dist");
        if (!distDir.isDirectory()) {
            log.error("构建命令已经结束，但没有生成 dist 目录：{}", distDir.getAbsolutePath());
            return false;
        }
        log.info("Vue 项目构建成功，静态文件目录：{}", distDir.getAbsolutePath());
        return true;
    }

    /**
     * 安装 package.json 中声明的依赖，最长等待五分钟。
     */
    private boolean executeNpmInstall(File projectDir) {
        return executeCommand(
                projectDir,
                new String[]{buildCommand("npm"), "install"},
                INSTALL_TIMEOUT_SECONDS
        );
    }

    /**
     * 调用项目自身的 build 脚本，最长等待三分钟。
     */
    private boolean executeNpmBuild(File projectDir) {
        return executeCommand(
                projectDir,
                new String[]{buildCommand("npm"), "run", "build"},
                BUILD_TIMEOUT_SECONDS
        );
    }

    /**
     * 在指定目录运行外部命令，并等待它在限定时间内结束。
     *
     * 标准输出和错误输出必须在进程运行期间持续读取。否则 npm 输出较多时可能填满系统
     * 管道缓冲区，子进程会停下来等待 Java 读取，Java 又在等待子进程退出，最终形成阻塞。
     *
     * @param workingDir 命令的工作目录
     * @param command 命令及参数，每个数组元素都是一个独立参数
     * @param timeoutSeconds 最长等待时间，单位为秒
     * @return 进程在超时前以退出码 0 结束时返回 true
     */
    private boolean executeCommand(File workingDir, String[] command, int timeoutSeconds) {
        String commandText = String.join(" ", command);
        try {
            log.info("在目录 {} 中执行命令：{}", workingDir.getAbsolutePath(), commandText);
            Process process = RuntimeUtil.exec(null, workingDir, command);
            consumeProcessOutput(process.getInputStream(), false, commandText);
            consumeProcessOutput(process.getErrorStream(), true, commandText);

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                log.error("命令执行超过 {} 秒，正在终止：{}", timeoutSeconds, commandText);
                process.destroyForcibly();
                return false;
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                log.error("命令执行失败，退出码为 {}：{}", exitCode, commandText);
                return false;
            }
            log.info("命令执行成功：{}", commandText);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("等待命令执行时线程被中断：{}", commandText, e);
            return false;
        } catch (Exception e) {
            log.error("命令执行异常：{}", commandText, e);
            return false;
        }
    }

    /**
     * 使用独立虚拟线程读取一条进程输出流，避免外部进程因输出缓冲区满而停住。
     */
    private void consumeProcessOutput(InputStream inputStream,
                                      boolean errorOutput,
                                      String commandText) {
        Thread.ofVirtual().start(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    inputStream,
                    Charset.defaultCharset()
            ))) {
                reader.lines().forEach(line -> {
                    if (errorOutput) {
                        log.warn("[{}] {}", commandText, line);
                    } else {
                        log.info("[{}] {}", commandText, line);
                    }
                });
            } catch (Exception e) {
                log.debug("读取命令输出结束：{}", commandText, e);
            }
        });
    }

    /**
     * Windows 通过 npm.cmd 启动命令，Linux 和 macOS 直接使用 npm。
     */
    private String buildCommand(String baseCommand) {
        return isWindows() ? baseCommand + ".cmd" : baseCommand;
    }

    /**
     * 根据 JVM 报告的操作系统名称判断当前是否运行在 Windows。
     */
    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("windows");
    }
}
