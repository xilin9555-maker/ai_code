package com.tmz.aicode.service;

/**
 * 网页截图服务。
 *
 * 该服务把本地截图和对象存储上传组合成一个完整能力。调用方只需要提供可以访问的网页地址，
 * 不需要了解浏览器驱动、本地临时目录或 COS 对象键的生成方式。
 */
public interface ScreenshotService {

    /**
     * 生成网页截图并上传到对象存储。
     *
     * @param webUrl 需要截图的完整网页地址
     * @return 上传成功后的图片公开访问地址
     */
    String generateAndUploadScreenshot(String webUrl);
}
