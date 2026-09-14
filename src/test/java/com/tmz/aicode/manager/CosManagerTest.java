package com.tmz.aicode.manager;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import com.tmz.aicode.config.CosClientConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 只验证对象键传递和访问地址拼接，不会向真实的腾讯云存储桶发送请求。
 */
@ExtendWith(MockitoExtension.class)
class CosManagerTest {

    @Mock
    private COSClient cosClient;

    private CosClientConfig cosClientConfig;

    private CosManager cosManager;

    @BeforeEach
    void setUp() {
        cosClientConfig = new CosClientConfig();
        cosClientConfig.setBucket("ai-code-test-123456");
        cosClientConfig.setHost("https://example.cos.test/");
        cosManager = new CosManager(cosClientConfig, cosClient);
    }

    @Test
    void putObjectShouldSendConfiguredBucketKeyAndFileToCos() {
        File file = new File("cover.jpg");
        PutObjectResult expectedResult = new PutObjectResult();
        when(cosClient.putObject(any(PutObjectRequest.class))).thenReturn(expectedResult);

        PutObjectResult actualResult = cosManager.putObject("/screenshots/cover.jpg", file);

        assertSame(expectedResult, actualResult);
        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(cosClient).putObject(requestCaptor.capture());
        PutObjectRequest request = requestCaptor.getValue();
        assertEquals("ai-code-test-123456", request.getBucketName());
        assertEquals("/screenshots/cover.jpg", request.getKey());
        assertSame(file, request.getFile());
    }

    @Test
    void uploadFileShouldReturnNormalizedPublicUrl() {
        File file = new File("cover.jpg");
        when(cosClient.putObject(any(PutObjectRequest.class))).thenReturn(new PutObjectResult());

        String fileUrl = cosManager.uploadFile("/screenshots/2026/09/13/cover.jpg", file);

        assertEquals("https://example.cos.test/screenshots/2026/09/13/cover.jpg", fileUrl);
    }

    @Test
    void uploadFileShouldReturnNullWhenCosReturnsNoResult() {
        File file = new File("cover.jpg");
        when(cosClient.putObject(any(PutObjectRequest.class))).thenReturn(null);

        String fileUrl = cosManager.uploadFile("/screenshots/cover.jpg", file);

        assertNull(fileUrl);
    }
}
