package com.tmz.aicode.innerservice;

/**
 * 网页截图服务向其他服务暴露的内部接口。
 */
public interface InnerScreenshotService {

    /**
     * 为指定网页生成截图并上传到对象存储。
     *
     * @param webUrl 可访问的完整网页地址
     * @return 上传完成后的图片访问地址
     */
    String generateAndUploadScreenshot(String webUrl);
}
