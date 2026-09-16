package com.tmz.aicode.ai;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 锁定原生应用全量修改所依赖的提示词协议。
 *
 * 这些测试只读取本地资源，不访问真实模型。它们用于防止以后精简提示词时误删完整文件、
 * 代码块数量或最小修改范围等关键约束，导致解析器保存局部代码。
 */
class NativeCodePromptContractTest {

    @Test
    void htmlStreamPromptRequiresOneCompleteCodeBlock() throws IOException {
        String prompt = readPrompt("prompt/codegen-html-stream-system-prompt.txt");

        assertAll(
                () -> assertTrue(prompt.contains("最新完整页面")),
                () -> assertTrue(prompt.contains("只改动用户明确要求")),
                () -> assertTrue(prompt.contains("最多且只能输出 1 个使用 html")),
                () -> assertTrue(prompt.contains("完整页面")),
                () -> assertTrue(prompt.contains("绝不能只返回目标元素")),
                () -> assertTrue(prompt.contains("## 本次修改")),
                () -> assertTrue(prompt.contains("不要把未改动的原有功能写成本次修改"))
        );
    }

    @Test
    void multiFileStreamPromptRequiresOneCompleteBlockPerFile() throws IOException {
        String prompt = readPrompt("prompt/codegen-multi-file-stream-system-prompt.txt");

        assertAll(
                () -> assertTrue(prompt.contains("最新三个完整文件")),
                () -> assertTrue(prompt.contains("未受影响的文件也要完整返回")),
                () -> assertTrue(prompt.contains("最多且只能输出 1 个 html 代码块")),
                () -> assertTrue(prompt.contains("1 个 css 代码块")),
                () -> assertTrue(prompt.contains("1 个 javascript 代码块")),
                () -> assertTrue(prompt.contains("## 本次修改")),
                () -> assertTrue(prompt.contains("并标明涉及的文件"))
        );
    }

    @Test
    void structuredPromptsKeepFullFileJsonContract() throws IOException {
        String htmlPrompt = readPrompt("prompt/codegen-html-system-prompt.txt");
        String multiFilePrompt = readPrompt("prompt/codegen-multi-file-system-prompt.txt");

        assertAll(
                () -> assertTrue(htmlPrompt.contains("htmlCode 也必须返回修改后的完整 HTML 文档")),
                () -> assertTrue(htmlPrompt.contains("只返回一个合法的 JSON 对象")),
                () -> assertTrue(htmlPrompt.contains("还必须追加“本次修改”")),
                () -> assertTrue(multiFilePrompt.contains("三个文件的完整最新内容")),
                () -> assertTrue(multiFilePrompt.contains("只返回一个合法的 JSON 对象")),
                () -> assertTrue(multiFilePrompt.contains("未受影响的文件同样要完整返回")),
                () -> assertTrue(multiFilePrompt.contains("还必须追加“本次修改”"))
        );
    }

    private String readPrompt(String resourcePath) throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            assertNotNull(input, "提示词资源不存在：" + resourcePath);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
