package com.tmz.aicode.model.dto.build;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Vue 工程构建进度。
 *
 * 构建器只上报少量稳定阶段，不直接转发 npm 的大量日志。这样既能让前端及时说明当前
 * 正在做什么，也不会让命令输出占满 SSE 连接或污染聊天记录。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BuildProgress {

    public static final String EVENT_BUILD_START = "build_start";

    public static final String EVENT_BUILD_PROGRESS = "build_progress";

    public static final String EVENT_BUILD_COMPLETE = "build_complete";

    public static final String STATUS_RUNNING = "running";

    public static final String STATUS_SUCCESS = "success";

    public static final String STATUS_FAILED = "failed";

    /** 前端监听的 SSE 事件名称。 */
    private String event;

    /** 稳定的构建阶段标识，便于前端按阶段展示。 */
    private String stage;

    /** 当前阶段状态：running、success 或 failed。 */
    private String status;

    /** 估算进度，范围为 0 到 100。 */
    private int percent;

    /** 面向用户的简短进度说明。 */
    private String message;

    /** 创建构建开始事件。 */
    public static BuildProgress started(String stage, int percent, String message) {
        return new BuildProgress(
                EVENT_BUILD_START, stage, STATUS_RUNNING, percent, message);
    }

    /** 创建构建过程事件。 */
    public static BuildProgress running(String stage, int percent, String message) {
        return new BuildProgress(
                EVENT_BUILD_PROGRESS, stage, STATUS_RUNNING, percent, message);
    }

    /** 创建阶段成功事件。 */
    public static BuildProgress succeeded(String stage, int percent, String message) {
        return new BuildProgress(
                EVENT_BUILD_PROGRESS, stage, STATUS_SUCCESS, percent, message);
    }

    /** 创建构建失败事件。 */
    public static BuildProgress failed(String stage, int percent, String message) {
        return new BuildProgress(
                EVENT_BUILD_PROGRESS, stage, STATUS_FAILED, percent, message);
    }

    /** 创建整个构建完成事件。 */
    public static BuildProgress completed(String message) {
        return new BuildProgress(
                EVENT_BUILD_COMPLETE, "completed", STATUS_SUCCESS, 100, message);
    }
}
