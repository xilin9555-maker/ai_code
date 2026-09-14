package com.tmz.aicode.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import com.tmz.aicode.exception.ErrorCode;
import com.tmz.aicode.exception.ThrowUtils;
import com.tmz.aicode.manager.CosManager;
import com.tmz.aicode.service.ScreenshotService;
import com.tmz.aicode.utils.WebScreenshotUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 网页截图服务实现。
 *
 * 执行顺序为：生成本地压缩截图、上传到 COS、返回公开地址、清理本地临时目录。
 * 这里不处理应用编号和数据库字段，因而也可以被其他需要网页截图的业务复用。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ScreenshotServiceImpl implements ScreenshotService {

    private static final DateTimeFormatter DATE_PATH_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final CosManager cosManager;

    /**
     * 生成网页截图并上传到对象存储。
     *
     * 本地截图只承担上传过程中的临时中转作用。上传结束后，不论结果成功还是发生异常，
     * {@code finally} 都会删除本次截图目录，防止服务器磁盘被临时文件持续占用。
     *
     * @param webUrl 需要截图的完整网页地址
     * @return COS 中图片的公开访问地址
     */
    @Override
    public String generateAndUploadScreenshot(String webUrl) {
        ThrowUtils.throwIf(StrUtil.isBlank(webUrl),
                ErrorCode.PARAMS_ERROR, "网页地址不能为空");
        log.info("开始生成网页截图，地址：{}", webUrl);

        String localScreenshotPath = WebScreenshotUtils.saveWebPageScreenshot(webUrl);
        ThrowUtils.throwIf(StrUtil.isBlank(localScreenshotPath),
                ErrorCode.OPERATION_ERROR, "本地截图生成失败");

        try {
            String screenshotUrl = uploadScreenshotToCos(localScreenshotPath);
            ThrowUtils.throwIf(StrUtil.isBlank(screenshotUrl),
                    ErrorCode.OPERATION_ERROR, "截图上传对象存储失败");
            log.info("网页截图生成并上传成功：{} -> {}", webUrl, screenshotUrl);
            return screenshotUrl;
        } finally {
            cleanupLocalFile(localScreenshotPath);
        }
    }

    /**
     * 把已经压缩的本地截图上传到 COS。
     *
     * 上传时重新生成文件名，避免不同服务器或并发任务生成相同的本地文件名后互相覆盖。
     *
     * @param localScreenshotPath 本地压缩截图的绝对路径
     * @return 图片公开访问地址；本地文件无效时返回 null
     */
    private String uploadScreenshotToCos(String localScreenshotPath) {
        if (StrUtil.isBlank(localScreenshotPath)) {
            return null;
        }

        File screenshotFile = FileUtil.file(localScreenshotPath);
        if (!screenshotFile.isFile()) {
            log.error("待上传的截图文件不存在：{}", localScreenshotPath);
            return null;
        }

        String fileName = UUID.fastUUID().toString(true).substring(0, 8)
                + "_compressed.jpg";
        String cosKey = generateScreenshotKey(fileName);
        return cosManager.uploadFile(cosKey, screenshotFile);
    }

    /**
     * 按日期生成截图对象键。
     *
     * 例如 {@code /screenshots/2026/09/13/a1b2c3d4_compressed.jpg}。日期分层可以避免
     * 所有文件堆在同一级目录中，也方便按日期查看和清理历史文件。
     */
    private String generateScreenshotKey(String fileName) {
        String datePath = LocalDate.now().format(DATE_PATH_FORMATTER);
        return String.format("/screenshots/%s/%s", datePath, fileName);
    }

    /**
     * 删除本次截图产生的临时目录。
     *
     * 每次截图都有独立目录，因此删除父目录可以同时清理可能残留的原始 PNG 和压缩 JPG。
     *
     * @param localFilePath 本地压缩截图路径
     */
    private void cleanupLocalFile(String localFilePath) {
        File localFile = FileUtil.file(localFilePath);
        if (!localFile.exists()) {
            return;
        }

        File parentDirectory = localFile.getParentFile();
        if (parentDirectory != null) {
            FileUtil.del(parentDirectory);
            log.info("本地截图临时目录已清理：{}", parentDirectory.getAbsolutePath());
        }
    }
}
