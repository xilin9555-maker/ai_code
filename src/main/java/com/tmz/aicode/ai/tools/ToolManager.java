package com.tmz.aicode.ai.tools;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一注册并管理所有 AI 工具。
 *
 * Spring 会把 BaseTool 的全部实现以数组形式注入。管理器一方面向 LangChain4j 提供完整
 * 工具数组，另一方面维护工具名称到展示策略的映射，供流处理器格式化工具调用信息。
 */
@Slf4j
@Component
public class ToolManager {

    private final BaseTool[] tools;
    private final Map<String, BaseTool> toolMap;

    /**
     * 初始化工具注册表。
     *
     * 工具名称是模型事件和 Java 实例之间的唯一关联键，因此空名称或重复名称都属于配置
     * 错误，应当在应用启动阶段立即失败，而不是等到用户生成项目时才暴露问题。
     *
     * @param tools Spring 容器中全部 BaseTool 实现
     */
    public ToolManager(BaseTool[] tools) {
        this.tools = tools.clone();
        Map<String, BaseTool> registeredTools = new LinkedHashMap<>();
        for (BaseTool tool : this.tools) {
            if (tool == null || StrUtil.isBlank(tool.getToolName())) {
                throw new IllegalStateException("AI 工具名称不能为空");
            }
            BaseTool previous = registeredTools.putIfAbsent(tool.getToolName(), tool);
            if (previous != null) {
                throw new IllegalStateException("AI 工具名称重复：" + tool.getToolName());
            }
            log.info("注册 AI 工具：{} -> {}", tool.getToolName(), tool.getDisplayName());
        }
        this.toolMap = Map.copyOf(registeredTools);
        log.info("AI 工具管理器初始化完成，共注册 {} 个工具", toolMap.size());
    }

    /**
     * 根据模型返回的方法名获取对应工具。
     *
     * @param toolName LangChain4j 工具事件中的方法名
     * @return 已注册工具；名称未知时返回 null，由调用方生成兼容提示
     */
    public BaseTool getTool(String toolName) {
        return toolMap.get(toolName);
    }

    /**
     * 获取供 LangChain4j 注册的全部工具。
     *
     * 返回数组是为了正确匹配 AiServices.Builder.tools(Object...) 的可变参数签名。每次
     * 返回副本，防止调用方替换数组元素后影响管理器内部状态。
     *
     * @return 全部已注册工具的数组副本
     */
    public BaseTool[] getAllTools() {
        return tools.clone();
    }
}
