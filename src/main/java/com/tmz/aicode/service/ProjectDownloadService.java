package com.tmz.aicode.service;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 项目代码下载服务。
 *
 * 该服务只关心如何过滤并压缩一个已经确定的项目目录。应用是否存在、当前用户能否下载
 * 等业务规则由接口层处理，使压缩能力以后也能被其他受控场景复用。
 */
public interface ProjectDownloadService {

    /**
     * 将项目源代码压缩成 ZIP 文件并直接写入 HTTP 响应。
     *
     * @param projectPath     需要压缩的项目根目录
     * @param downloadFileName 返回给浏览器的文件名，不包含 {@code .zip} 后缀
     * @param response        当前 HTTP 响应，用于写入响应头和 ZIP 二进制内容
     */
    void downloadProjectAsZip(String projectPath,
                              String downloadFileName,
                              HttpServletResponse response);
}
