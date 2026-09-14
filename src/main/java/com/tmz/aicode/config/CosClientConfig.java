package com.tmz.aicode.config;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.region.Region;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 腾讯云 COS 客户端配置。
 *
 * 配置内容从 {@code cos.client} 读取，其中访问密钥应放在本地私密配置文件或环境变量中，
 * 不应提交到代码仓库。该类只负责创建 SDK 客户端，不包含文件路径等业务规则。
 */
@Configuration
@ConfigurationProperties(prefix = "cos.client")
@Data
public class CosClientConfig {

    /**
     * 文件上传成功后的公开访问域名，需要包含 {@code http://} 或 {@code https://}。
     */
    private String host;

    /**
     * 腾讯云 API 密钥标识。
     */
    private String secretId;

    /**
     * 腾讯云 API 密钥内容，只应保存在本地私密配置中。
     */
    private String secretKey;

    /**
     * 存储桶所在地域，例如 {@code ap-shanghai}。
     */
    private String region;

    /**
     * COS 存储桶名称，通常包含应用编号后缀。
     */
    private String bucket;

    /**
     * 创建供文件管理组件复用的 COS 客户端。
     *
     * Spring 容器停止时会调用 {@link COSClient#shutdown()}，及时释放 SDK 使用的连接资源。
     */
    @Bean(destroyMethod = "shutdown")
    public COSClient cosClient() {
        COSCredentials credentials = new BasicCOSCredentials(secretId, secretKey);
        ClientConfig clientConfig = new ClientConfig(new Region(region));
        return new COSClient(credentials, clientConfig);
    }
}
