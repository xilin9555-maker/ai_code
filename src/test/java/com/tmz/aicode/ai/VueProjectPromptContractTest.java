package com.tmz.aicode.ai;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 锁定 Vue 工程增量修改依赖的提示词规则。
 *
 * 测试只读取本地提示词资源，防止后续调整提示词时重新要求模型用 writeFile 覆盖整个
 * 组件，导致前端在小范围修改时再次展示完整文件代码。
 */
class VueProjectPromptContractTest {

    /** 已有文件必须先读取，再通过最小且唯一的旧片段进行局部替换。 */
    @Test
    void existingComponentsMustUseIncrementalModification() throws IOException {
        String prompt = readPrompt();

        assertAll(
                () -> assertTrue(prompt.contains("先调用 `readDir`")),
                () -> assertTrue(prompt.contains("再调用 `readFile`")),
                () -> assertTrue(prompt.contains("只改动用户明确要求")),
                () -> assertTrue(prompt.contains("已有文件的局部修改必须使用 `modifyFile`")),
                () -> assertTrue(prompt.contains("唯一定位修改位置的最小完整片段")),
                () -> assertTrue(prompt.contains("禁止使用 `writeFile` 覆盖整个文件")),
                () -> assertTrue(prompt.contains("只展示本次实际修改的片段")),
                () -> assertTrue(prompt.contains("目标模块必须真实导出同名成员")),
                () -> assertTrue(prompt.contains("必须先调用函数再解构使用")),
                () -> assertTrue(prompt.contains("单次响应输出控制在 8192 Token 以内")),
                () -> assertTrue(prompt.contains("应分成多轮调用文件工具"))
        );
    }

    private String readPrompt() throws IOException {
        String resourcePath = "prompt/codegen-vue-project-system-prompt.txt";
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            assertNotNull(input, "提示词资源不存在：" + resourcePath);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
