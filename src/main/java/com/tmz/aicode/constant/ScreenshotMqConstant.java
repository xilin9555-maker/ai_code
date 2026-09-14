package com.tmz.aicode.constant;

/**
 * 应用截图消息队列使用的名称。
 *
 * 生产者、消费者和 RabbitMQ 声明共用这里的常量，可以避免某一处修改名称后消息
 * 无法送达。队列和交换机名称带上业务前缀，也便于在 RabbitMQ 管理页面中定位。
 */
public final class ScreenshotMqConstant {

    /** 接收待处理截图任务的交换机。 */
    public static final String EXCHANGE_NAME = "ai-code.screenshot.exchange";

    /** 保存待处理截图任务的持久化队列。 */
    public static final String QUEUE_NAME = "ai-code.screenshot.queue";

    /** 把截图消息从交换机路由到主队列的键。 */
    public static final String ROUTING_KEY = "ai-code.screenshot.generate";

    /** 接收最终失败任务的死信交换机。 */
    public static final String DEAD_LETTER_EXCHANGE_NAME = "ai-code.screenshot.dlx";

    /** 保存重试后仍处理失败消息的队列，方便人工排查和补偿。 */
    public static final String DEAD_LETTER_QUEUE_NAME = "ai-code.screenshot.dead.queue";

    /** 把死信路由到死信队列的键。 */
    public static final String DEAD_LETTER_ROUTING_KEY = "ai-code.screenshot.dead";

    private ScreenshotMqConstant() {
        // 该类只提供常量，不需要创建实例。
    }
}
