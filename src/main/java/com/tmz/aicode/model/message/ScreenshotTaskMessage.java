package com.tmz.aicode.model.message;

/**
 * 部署完成后发送给截图消费者的任务消息。
 *
 * 消息只保存重新执行截图所必需的两个值。消费者收到任务后会重新查询应用状态，
 * 不把整个应用实体塞进消息，避免数据库字段调整时旧消息难以兼容。
 *
 * @param appId  已部署应用的 id
 * @param appUrl 截图浏览器能够访问的部署地址
 */
public record ScreenshotTaskMessage(Long appId, String appUrl) {
}
