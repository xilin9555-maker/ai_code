package com.tmz.aicode.innerservice.impl;

import com.tmz.aicode.innerservice.InnerScreenshotService;
import com.tmz.aicode.service.ScreenshotService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * 截图内部服务的 Dubbo 提供方实现。
 *
 * 远程接口保持轻量，只接收网页地址并返回已上传截图的访问地址。
 */
@DubboService
@Slf4j
public class InnerScreenshotServiceImpl implements InnerScreenshotService {

    @Resource
    private ScreenshotService screenshotService;

    /**
     * 调用本地截图业务完成浏览器渲染、压缩和对象存储上传。
     *
     * @param webUrl 可访问的完整网页地址
     * @return 上传后的截图地址
     */
    @Override
    public String generateAndUploadScreenshot(String webUrl) {
        log.info("收到远程截图请求，网页地址：{}", webUrl);
        return screenshotService.generateAndUploadScreenshot(webUrl);
    }
}
