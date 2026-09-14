package com.tmz.aicode.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tmz.aicode.constant.ScreenshotMqConstant;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 应用截图队列配置。
 *
 * 主队列保存等待截图的任务，并通过 DirectExchange 的精确路由键接收消息。任务连续
 * 失败后会进入死信队列，不会在主队列中无限循环，也不会因为一次坏任务堵住后续任务。
 */
@Configuration
public class ScreenshotMqConfig {

    /**
     * 声明截图任务交换机。durable 为 true，RabbitMQ 重启后仍保留交换机。
     */
    @Bean
    public DirectExchange screenshotExchange() {
        return new DirectExchange(ScreenshotMqConstant.EXCHANGE_NAME, true, false);
    }

    /**
     * 声明截图主队列，并指定消息被拒绝后的死信去向。
     */
    @Bean
    public Queue screenshotQueue() {
        return QueueBuilder.durable(ScreenshotMqConstant.QUEUE_NAME)
                .deadLetterExchange(ScreenshotMqConstant.DEAD_LETTER_EXCHANGE_NAME)
                .deadLetterRoutingKey(ScreenshotMqConstant.DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    /**
     * 使用固定路由键连接截图交换机和主队列。
     */
    @Bean
    public Binding screenshotBinding(Queue screenshotQueue,
                                     DirectExchange screenshotExchange) {
        return BindingBuilder.bind(screenshotQueue)
                .to(screenshotExchange)
                .with(ScreenshotMqConstant.ROUTING_KEY);
    }

    /**
     * 声明死信交换机，用于接收已经耗尽重试次数的任务。
     */
    @Bean
    public DirectExchange screenshotDeadLetterExchange() {
        return new DirectExchange(
                ScreenshotMqConstant.DEAD_LETTER_EXCHANGE_NAME,
                true,
                false
        );
    }

    /**
     * 死信队列不再配置新的死信去向，防止失败消息反复循环。
     */
    @Bean
    public Queue screenshotDeadLetterQueue() {
        return QueueBuilder.durable(ScreenshotMqConstant.DEAD_LETTER_QUEUE_NAME).build();
    }

    /**
     * 把死信交换机和死信队列连接起来，失败任务最终会停留在这里等待处理。
     */
    @Bean
    public Binding screenshotDeadLetterBinding(Queue screenshotDeadLetterQueue,
                                               DirectExchange screenshotDeadLetterExchange) {
        return BindingBuilder.bind(screenshotDeadLetterQueue)
                .to(screenshotDeadLetterExchange)
                .with(ScreenshotMqConstant.DEAD_LETTER_ROUTING_KEY);
    }

    /**
     * 使用 JSON 传输任务，RabbitMQ 管理页面可以直接阅读消息内容，也能避免 Java
     * 原生序列化对类版本和安全白名单的额外依赖。
     */
    @Bean
    public MessageConverter rabbitMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
