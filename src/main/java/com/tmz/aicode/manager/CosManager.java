package com.tmz.aicode.manager;

import cn.hutool.core.util.StrUtil;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import com.tmz.aicode.config.CosClientConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * COS 对象存储管理器。
 *
 * 该组件集中封装腾讯云 SDK 的文件上传操作，让上层业务只关心对象键、待上传文件和最终访问地址。
 * 截图文件的命名、日期目录以及应用封面更新等业务规则仍由对应的服务层负责。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CosManager {

    private final CosClientConfig cosClientConfig;

    private final COSClient cosClient;

    /**
     * 将本地文件上传到指定对象键。
     *
     * @param key  COS 中用于唯一定位文件的对象键，例如 {@code /screenshots/2026/09/13/cover.jpg}
     * @param file 需要上传的本地文件
     * @return 腾讯云 SDK 返回的上传结果
     */
    public PutObjectResult putObject(String key, File file) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(
                cosClientConfig.getBucket(),
                key,
                file
        );
        return cosClient.putObject(putObjectRequest);
    }

    /**
     * 上传本地文件并返回它的公开访问地址。
     *
     * 文件真正写入 COS 后，访问地址由配置的域名和对象键拼接得到。拼接前会统一处理斜杠，
     * 避免域名末尾和对象键开头同时包含斜杠时产生重复路径分隔符。
     *
     * @param key  COS 对象键
     * @param file 需要上传的本地文件
     * @return 文件的公开访问地址；SDK 没有返回上传结果时返回 null
     */
    public String uploadFile(String key, File file) {
        PutObjectResult result = putObject(key, file);
        if (result == null) {
            log.error("文件上传 COS 失败，SDK 返回结果为空，对象键：{}", key);
            return null;
        }

        String fileUrl = buildFileUrl(cosClientConfig.getHost(), key);
        log.info("文件上传 COS 成功：{} -> {}", file.getName(), fileUrl);
        return fileUrl;
    }

    /**
     * 组合访问域名和对象键，保证二者之间只有一个斜杠。
     */
    private String buildFileUrl(String host, String key) {
        String normalizedHost = StrUtil.removeSuffix(host, "/");
        String normalizedKey = StrUtil.addPrefixIfNot(key, "/");
        return normalizedHost + normalizedKey;
    }
}
