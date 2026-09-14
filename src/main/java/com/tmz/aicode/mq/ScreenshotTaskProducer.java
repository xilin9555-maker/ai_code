package com.tmz.aicode.mq;

import cn.hutool.core.util.StrUtil;
import com.tmz.aicode.constant.ScreenshotMqConstant;
import com.tmz.aicode.exception.ErrorCode;
import com.tmz.aicode.exception.ThrowUtils;
import com.tmz.aicode.model.message.ScreenshotTaskMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 应用截图任务生产者。
 *
 * 生产者只负责校验并投递任务，不在部署请求线程中打开浏览器。消息成功进入 RabbitMQ
 * 后，部署接口便可以继续返回，耗时的截图工作交给消费者完成。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ScreenshotTaskProducer {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 把一个已部署应用提交到截图队列。
     *
     * @param appId  已部署应用的 id
     * @param appUrl 截图浏览器能够访问的部署地址
     */
    public void sendScreenshotTask(Long appId, String appUrl) {
        ThrowUtils.throwIf(appId == null || appId <= 0,
                ErrorCode.PARAMS_ERROR, "应用 id 不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(appUrl),
                ErrorCode.PARAMS_ERROR, "应用访问地址不能为空");

        ScreenshotTaskMessage message = new ScreenshotTaskMessage(appId, appUrl.trim());
        rabbitTemplate.convertAndSend(
                ScreenshotMqConstant.EXCHANGE_NAME,
                ScreenshotMqConstant.ROUTING_KEY,
                message,
                rabbitMessage -> {
                    // 主队列本身是持久化的，消息也标记为持久化后才能在 Broker 重启后恢复。
                    rabbitMessage.getMessageProperties()
                            .setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    return rabbitMessage;
                }
        );
        log.info("应用截图任务已进入消息队列，应用 id：{}，应用地址：{}", appId, appUrl);
    }
}
