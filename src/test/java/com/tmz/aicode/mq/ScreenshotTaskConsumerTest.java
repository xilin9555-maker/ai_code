package com.tmz.aicode.mq;

import com.tmz.aicode.exception.BusinessException;
import com.tmz.aicode.model.entity.App;
import com.tmz.aicode.model.message.ScreenshotTaskMessage;
import com.tmz.aicode.service.AppService;
import com.tmz.aicode.service.ScreenshotService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 截图任务消费者的本地单元测试。
 *
 * 截图服务、应用服务和数据库更新均由模拟对象替代，不会打开浏览器或访问 COS、数据库。
 */
class ScreenshotTaskConsumerTest {

    @Test
    void generatesScreenshotAndUpdatesAppCover() {
        ScreenshotService screenshotService = mock(ScreenshotService.class);
        AppService appService = mock(AppService.class);
        ScreenshotTaskConsumer consumer = new ScreenshotTaskConsumer(screenshotService, appService);
        long appId = 920201L;
        String appUrl = "http://localhost/aB3xY9/";
        String coverUrl = "https://example.cos.test/screenshots/cover.jpg";
        when(appService.getById(appId)).thenReturn(App.builder().id(appId).build());
        when(screenshotService.generateAndUploadScreenshot(appUrl)).thenReturn(coverUrl);
        when(appService.updateById(any(App.class))).thenReturn(true);

        consumer.consume(new ScreenshotTaskMessage(appId, appUrl));

        ArgumentCaptor<App> appCaptor = ArgumentCaptor.forClass(App.class);
        verify(appService).updateById(appCaptor.capture());
        assertEquals(appId, appCaptor.getValue().getId());
        assertEquals(coverUrl, appCaptor.getValue().getCover());
    }

    @Test
    void skipsScreenshotWhenAppNoLongerExists() {
        ScreenshotService screenshotService = mock(ScreenshotService.class);
        AppService appService = mock(AppService.class);
        ScreenshotTaskConsumer consumer = new ScreenshotTaskConsumer(screenshotService, appService);
        long appId = 920202L;
        when(appService.getById(appId)).thenReturn(null);

        consumer.consume(new ScreenshotTaskMessage(appId, "http://localhost/removed/"));

        verify(screenshotService, never()).generateAndUploadScreenshot(any());
        verify(appService, never()).updateById(any(App.class));
    }

    @Test
    void propagatesFailureSoListenerCanRetryMessage() {
        ScreenshotService screenshotService = mock(ScreenshotService.class);
        AppService appService = mock(AppService.class);
        ScreenshotTaskConsumer consumer = new ScreenshotTaskConsumer(screenshotService, appService);
        long appId = 920203L;
        String appUrl = "http://localhost/retry/";
        when(appService.getById(appId)).thenReturn(App.builder().id(appId).build());
        when(screenshotService.generateAndUploadScreenshot(appUrl))
                .thenThrow(new BusinessException(50000, "截图失败"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> consumer.consume(new ScreenshotTaskMessage(appId, appUrl))
        );

        assertEquals("截图失败", exception.getMessage());
        verify(appService, never()).updateById(any(App.class));
    }
}
