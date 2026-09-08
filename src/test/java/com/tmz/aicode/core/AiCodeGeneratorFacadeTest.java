package com.tmz.aicode.core;

import cn.hutool.core.io.FileUtil;
import com.tmz.aicode.ai.AiCodeGeneratorService;
import com.tmz.aicode.ai.model.HtmlCodeResult;
import com.tmz.aicode.ai.model.MultiFileCodeResult;
import com.tmz.aicode.model.enums.CodeGenTypeEnum;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 使用本地 Demo 服务验证门面模式的生成与保存流程。
 *
 * Demo 服务直接返回固定的结构化对象，不会访问网络，也不会消耗任何模型额度。
 * 测试关注门面是否选择了正确的生成方法，以及文件保存器是否写出了预期文件。
 */
class AiCodeGeneratorFacadeTest {

    private final List<File> generatedDirectories = new ArrayList<>();
    private final AiCodeGeneratorFacade facade = new AiCodeGeneratorFacade(new DemoAiCodeGeneratorService());

    /**
     * 验证单文件模式只生成 index.html，并且文件内容与 Demo 返回值一致。
     */
    @Test
    void generateAndSaveHtmlCode() {
        File outputDirectory = facade.generateAndSaveCode("生成一个欢迎页面", CodeGenTypeEnum.HTML);
        generatedDirectories.add(outputDirectory);

        File htmlFile = new File(outputDirectory, "index.html");
        assertTrue(htmlFile.isFile(), "单文件模式应生成 index.html");
        assertEquals(DemoAiCodeGeneratorService.HTML_CODE,
                FileUtil.readString(htmlFile, StandardCharsets.UTF_8));
        assertEquals(1, FileUtil.loopFiles(outputDirectory).size());
    }

    /**
     * 验证多文件模式分别生成 HTML、CSS 和 JavaScript 三个文件。
     */
    @Test
    void generateAndSaveMultiFileCode() {
        File outputDirectory = facade.generateAndSaveCode("生成一个作品展示页面", CodeGenTypeEnum.MULTI_FILE);
        generatedDirectories.add(outputDirectory);

        assertEquals(DemoAiCodeGeneratorService.HTML_CODE,
                readGeneratedFile(outputDirectory, "index.html"));
        assertEquals(DemoAiCodeGeneratorService.CSS_CODE,
                readGeneratedFile(outputDirectory, "style.css"));
        assertEquals(DemoAiCodeGeneratorService.JS_CODE,
                readGeneratedFile(outputDirectory, "script.js"));
        assertEquals(3, FileUtil.loopFiles(outputDirectory).size());
    }

    /**
     * 读取并确认指定文件确实已经生成。
     *
     * @param outputDirectory 本次生成结果目录
     * @param filename 需要检查的文件名
     * @return 文件中的 UTF-8 文本
     */
    private String readGeneratedFile(File outputDirectory, String filename) {
        File file = new File(outputDirectory, filename);
        assertTrue(file.isFile(), filename + " 应当已经生成");
        return FileUtil.readString(file, StandardCharsets.UTF_8);
    }

    /**
     * 每个测试完成后只删除本次创建的独立目录，避免 Demo 文件长期堆积。
     */
    @AfterEach
    void cleanGeneratedDirectories() {
        generatedDirectories.forEach(FileUtil::del);
    }

    /**
     * 为门面测试提供固定代码结果的本地实现。
     *
     * 它实现了与真实 AI 服务相同的接口，因此门面不需要知道结果来自本地 Demo 还是真实模型。
     */
    private static class DemoAiCodeGeneratorService implements AiCodeGeneratorService {

        private static final String HTML_CODE =
                "<!DOCTYPE html><html><body><h1>欢迎</h1></body></html>";
        private static final String CSS_CODE = "h1 { color: #2563eb; }";
        private static final String JS_CODE = "console.log('页面加载完成');";

        @Override
        public HtmlCodeResult generateHtmlCode(String userMessage) {
            HtmlCodeResult result = new HtmlCodeResult();
            result.setHtmlCode(HTML_CODE);
            result.setDescription("本地 Demo 生成的单文件页面");
            return result;
        }

        @Override
        public MultiFileCodeResult generateMultiFileCode(String userMessage) {
            MultiFileCodeResult result = new MultiFileCodeResult();
            result.setHtmlCode(HTML_CODE);
            result.setCssCode(CSS_CODE);
            result.setJsCode(JS_CODE);
            result.setDescription("本地 Demo 生成的多文件页面");
            return result;
        }
    }
}
