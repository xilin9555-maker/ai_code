package com.tmz.aicode.ai.tools;

import cn.hutool.json.JSONObject;

/**
 * 所有 AI 工具的统一展示契约。
 *
 * 工具本身负责执行业务操作，也最清楚自己的参数含义。把展示逻辑放在具体工具中，
 * 流处理器只需根据工具名称完成分发，不必为每种工具维护一组 if-else 判断。
 */
public abstract class BaseTool {

    /**
     * 获取 LangChain4j 暴露给模型的工具名称。
     *
     * 该名称必须与具体工具上标有 {@code @Tool} 的方法名保持一致，ToolManager 会用它
     * 关联流式工具事件和对应的展示策略。
     *
     * @return 工具方法的英文名称
     */
    public abstract String getToolName();

    /**
     * 获取面向用户展示的简短中文名称。
     *
     * @return 工具中文名称
     */
    public abstract String getDisplayName();

    /**
     * 生成模型刚选择该工具时的提示。
     *
     * 请求阶段的参数可能仍是不完整 JSON，因此这里只展示稳定的工具名称；详细参数在
     * 工具执行完成后再格式化，避免向前端发送错误或残缺的信息。
     *
     * @return 可以直接追加到 AI 回复中的 Markdown 文本
     */
    public String generateToolRequestResponse() {
        return String.format("\n\n[选择工具] %s\n\n", getDisplayName());
    }

    /**
     * 根据完整工具参数生成执行记录。
     *
     * @param arguments 工具执行时使用的完整 JSON 参数
     * @return 可以展示并保存到对话历史的 Markdown 文本
     */
    public abstract String generateToolExecutedResult(JSONObject arguments);
}
