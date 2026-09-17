package com.tmz.aicode.ai.tools;

import cn.hutool.json.JSONObject;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 允许模型在工程任务已经完成时主动结束工具调用循环。
 *
 * 连续调用次数上限负责异常情况下的强制兜底，本工具负责正常情况下的主动收尾。模型
 * 调用该工具后会收到明确的结束指令，从而转为输出最终结果，不再继续读写工程文件。
 */
@Slf4j
@Component
public class ExitTool extends BaseTool {

    /** 返回与工具方法名一致的稳定标识。 */
    @Override
    public String getToolName() {
        return "exit";
    }

    /** 返回供前端展示的工具名称。 */
    @Override
    public String getDisplayName() {
        return "退出工具调用";
    }

    /**
     * 主动结束当前工具调用过程。
     *
     * @return 提醒模型停止选择工具并生成最终回复的指令
     */
    @Tool("当任务已完成或无需继续调用工具时，使用此工具退出操作，防止循环")
    public String exit() {
        log.info("AI 请求退出工具调用");
        return "不要继续调用工具，可以输出最终结果了";
    }

    /**
     * 生成适合展示给用户的结束标记。
     *
     * exit 工具不接收业务参数，也不需要暴露内部指令，因此这里只返回简洁、稳定的状态
     * 文本，供流处理器追加到本轮回复中。
     *
     * @param arguments 框架传入的工具参数；exit 工具不会读取该参数
     * @return 工具调用结束标记
     */
    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        return "\n\n[执行结束]\n\n";
    }
}
