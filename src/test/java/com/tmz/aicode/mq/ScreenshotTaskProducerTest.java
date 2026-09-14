package com.tmz.aicode.mq;

import com.tmz.aicode.constant.ScreenshotMqConstant;
import com.tmz.aicode.model.message.ScreenshotTaskMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 截图任务生产者的本地单元测试。
 *
 * RabbitTemplate 使用模拟对象，测试只核对交换机、路由键和消息内容，不需要启动 RabbitMQ。
 */
class ScreenshotTaskProducerTest {

    @Test
    void sendsScreenshotTaskToExpectedRoute() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        ScreenshotTaskProducer producer = new ScreenshotTaskProducer(rabbitTemplate);
        long appId = 920101L;
        String appUrl = "http://localhost/aB3xY9/";

        producer.sendScreenshotTask(appId, appUrl);

        ArgumentCaptor<ScreenshotTaskMessage> messageCaptor =
                ArgumentCaptor.forClass(ScreenshotTaskMessage.class);
        ArgumentCaptor<MessagePostProcessor> processorCaptor =
                ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(rabbitTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.eq(ScreenshotMqConstant.EXCHANGE_NAME),
                org.mockito.ArgumentMatchers.eq(ScreenshotMqConstant.ROUTING_KEY),
                messageCaptor.capture(),
                processorCaptor.capture()
        );
        assertEquals(appId, messageCaptor.getValue().appId());
        assertEquals(appUrl, messageCaptor.getValue().appUrl());

        Message rabbitMessage = new Message(new byte[0], new MessageProperties());
        processorCaptor.getValue().postProcessMessage(rabbitMessage);
        assertEquals(
                MessageDeliveryMode.PERSISTENT,
                rabbitMessage.getMessageProperties().getDeliveryMode()
        );
    }
}
