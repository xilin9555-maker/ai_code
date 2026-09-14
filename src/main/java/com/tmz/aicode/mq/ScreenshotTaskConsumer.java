package com.tmz.aicode.mq;

import cn.hutool.core.util.StrUtil;
import com.tmz.aicode.constant.ScreenshotMqConstant;
import com.tmz.aicode.exception.BusinessException;
import com.tmz.aicode.exception.ErrorCode;
import com.tmz.aicode.model.entity.App;
import com.tmz.aicode.model.message.ScreenshotTaskMessage;
import com.tmz.aicode.service.AppService;
import com.tmz.aicode.service.ScreenshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 应用截图任务消费者。
 *
 * 消费并发固定为 1，同一个应用实例中的截图任务会逐个执行。共享 WebDriver 在完成
 * 当前页面截图前不会被另一条任务切换到其他地址，从而避免封面和应用对应错误。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ScreenshotTaskConsumer {

    private final ScreenshotService screenshotService;

    private final AppService appService;

    /**
     * 依次完成页面截图、COS 上传和数据库封面更新。
     *
     * 方法正常返回时，Spring AMQP 会确认消息；抛出异常时会根据本地配置重试三次，
     * 仍然失败的消息会被拒绝并转入死信队列。这里不吞掉异常，否则 RabbitMQ 会把
     * 尚未完成的任务误认为处理成功。
     *
     * @param message 生产者提交的截图任务
     */
    @RabbitListener(queues = ScreenshotMqConstant.QUEUE_NAME, concurrency = "1")
    public void consume(ScreenshotTaskMessage message) {
        validateMessage(message);

        // 消息可能在队列中等待一段时间，消费前确认应用仍然存在，避免给已删除应用上传图片。
        App app = appService.getById(message.appId());
        if (app == null) {
            log.warn("忽略已经不存在的应用截图任务，应用 id：{}", message.appId());
            return;
        }

        try {
            String screenshotUrl = screenshotService.generateAndUploadScreenshot(message.appUrl());
            App updateApp = new App();
            updateApp.setId(message.appId());
            updateApp.setCover(screenshotUrl);

            boolean updated = appService.updateById(updateApp);
            if (!updated) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "更新应用封面失败");
            }
            log.info("应用封面更新成功，应用 id：{}，封面地址：{}", message.appId(), screenshotUrl);
        } catch (Exception e) {
            // 保留任务上下文并继续抛出异常，让重试和死信机制接管失败消息。
            log.error("应用截图任务执行失败，应用 id：{}，应用地址：{}",
                    message.appId(), message.appUrl(), e);
            throw e;
        }
    }

    /**
     * 拒绝内容不完整的消息，防止浏览器收到空地址或数据库执行无意义更新。
     */
    private void validateMessage(ScreenshotTaskMessage message) {
        if (message == null
                || message.appId() == null
                || message.appId() <= 0
                || StrUtil.isBlank(message.appUrl())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "截图任务参数不完整");
        }
    }
}
