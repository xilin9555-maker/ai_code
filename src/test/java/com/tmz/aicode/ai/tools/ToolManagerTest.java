package com.tmz.aicode.ai.tools;

import cn.hutool.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证工具自动注册、名称查找和不同工具的展示策略。
 */
class ToolManagerTest {

    private final FileWriteTool fileWriteTool = new FileWriteTool();
    private final FileReadTool fileReadTool = new FileReadTool();
    private final FileModifyTool fileModifyTool = new FileModifyTool();
    private final FileDirReadTool fileDirReadTool = new FileDirReadTool();
    private final FileDeleteTool fileDeleteTool = new FileDeleteTool();

    /** 管理器应注册全部工具，并通过英文方法名返回原始实例。 */
    @Test
    void registersAndFindsAllTools() {
        ToolManager toolManager = createToolManager();

        assertEquals(5, toolManager.getAllTools().length);
        assertSame(fileWriteTool, toolManager.getTool("writeFile"));
        assertSame(fileReadTool, toolManager.getTool("readFile"));
        assertSame(fileModifyTool, toolManager.getTool("modifyFile"));
        assertSame(fileDirReadTool, toolManager.getTool("readDir"));
        assertSame(fileDeleteTool, toolManager.getTool("deleteFile"));
        assertNotSame(toolManager.getAllTools(), toolManager.getAllTools(),
                "工具数组应返回副本，不能暴露内部数组");
    }

    /** 五个工具应按照各自参数生成不同且可核对的用户反馈。 */
    @Test
    void generatesToolSpecificDisplayMessages() {
        JSONObject fileArguments = new JSONObject()
                .set("relativeFilePath", "src/App.vue")
                .set("content", "<template>首页</template>");
        JSONObject modifyArguments = new JSONObject()
                .set("relativeFilePath", "src/App.vue")
                .set("oldContent", "旧标题")
                .set("newContent", "新标题");

        String writeResult = fileWriteTool.generateToolExecutedResult(fileArguments);
        String modifyResult = fileModifyTool.generateToolExecutedResult(modifyArguments);

        assertEquals("\n\n[选择工具] 写入文件\n\n",
                fileWriteTool.generateToolRequestResponse());
        assertTrue(writeResult.contains("[工具调用] 写入文件 src/App.vue"));
        assertTrue(writeResult.contains("```vue\n<template>首页</template>\n```"));
        assertEquals("[工具调用] 读取文件 src/App.vue",
                fileReadTool.generateToolExecutedResult(fileArguments));
        assertTrue(modifyResult.contains("替换前：\n```\n旧标题\n```"));
        assertTrue(modifyResult.contains("替换后：\n```\n新标题\n```"));
        assertEquals("[工具调用] 读取目录 根目录",
                fileDirReadTool.generateToolExecutedResult(new JSONObject()));
        assertEquals("[工具调用] 删除文件 src/App.vue",
                fileDeleteTool.generateToolExecutedResult(fileArguments));
    }

    /** 重复工具名会导致流事件无法准确分发，必须在初始化阶段拒绝。 */
    @Test
    void rejectsDuplicateToolNames() {
        assertThrows(IllegalStateException.class, () -> new ToolManager(new BaseTool[]{
                fileReadTool,
                new DuplicateReadTool()
        }));
    }

    private ToolManager createToolManager() {
        return new ToolManager(new BaseTool[]{
                fileWriteTool,
                fileReadTool,
                fileModifyTool,
                fileDirReadTool,
                fileDeleteTool
        });
    }

    /** 仅用于构造重复名称场景的测试工具。 */
    private static class DuplicateReadTool extends BaseTool {

        @Override
        public String getToolName() {
            return "readFile";
        }

        @Override
        public String getDisplayName() {
            return "重复读取工具";
        }

        @Override
        public String generateToolExecutedResult(JSONObject arguments) {
            return "";
        }
    }
}
