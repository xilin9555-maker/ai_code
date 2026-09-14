package com.tmz.aicode.service.impl;

import com.tmz.aicode.exception.BusinessException;
import com.tmz.aicode.exception.ErrorCode;
import com.tmz.aicode.manager.CosManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 使用本地数据页面和模拟 COS 管理器验证完整截图服务，不会访问真实网站或上传真实文件。
 */
@ExtendWith(MockitoExtension.class)
class ScreenshotServiceImplTest {

    @Mock
    private CosManager cosManager;

    private ScreenshotServiceImpl screenshotService;

    @BeforeEach
    void setUp() {
        screenshotService = new ScreenshotServiceImpl(cosManager);
    }

    @Test
    void generateAndUploadScreenshotShouldRejectBlankUrl() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> screenshotService.generateAndUploadScreenshot(" "));

        assertEquals(ErrorCode.PARAMS_ERROR.getCode(), exception.getCode());
        assertEquals("网页地址不能为空", exception.getMessage());
    }

    @Test
    void generateAndUploadScreenshotShouldUploadAndCleanLocalDirectory() {
        String pageUrl = "data:text/html;charset=utf-8,"
                + "<html><body style='background:%23f4f1ea'>Screenshot Service</body></html>";
        String expectedUrl = "https://example.cos.test/screenshots/cover.jpg";
        AtomicReference<String> uploadedKey = new AtomicReference<>();
        AtomicReference<File> uploadedFile = new AtomicReference<>();
        when(cosManager.uploadFile(anyString(), any(File.class))).thenAnswer(invocation -> {
            uploadedKey.set(invocation.getArgument(0));
            File screenshotFile = invocation.getArgument(1);
            uploadedFile.set(screenshotFile);
            assertTrue(screenshotFile.isFile());
            assertTrue(screenshotFile.length() > 0);
            return expectedUrl;
        });

        String screenshotUrl = screenshotService.generateAndUploadScreenshot(pageUrl);

        assertEquals(expectedUrl, screenshotUrl);
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        assertTrue(uploadedKey.get().matches(
                "/screenshots/" + datePath + "/[0-9a-f]{8}_compressed\\.jpg"));
        assertFalse(uploadedFile.get().exists());
        assertFalse(uploadedFile.get().getParentFile().exists());
    }
}
