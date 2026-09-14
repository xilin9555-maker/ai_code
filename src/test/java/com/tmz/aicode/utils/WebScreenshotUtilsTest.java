package com.tmz.aicode.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 使用浏览器内置的数据页面验证截图流程，不依赖外部网站，也不会调用大模型。
 */
class WebScreenshotUtilsTest {

    @Test
    void saveWebPageScreenshot() throws Exception {
        String testUrl = "https://www.codefather.cn";
        String webPageScreenshot = WebScreenshotUtils.saveWebPageScreenshot(testUrl);
        Assertions.assertNotNull(webPageScreenshot);

    }
}
