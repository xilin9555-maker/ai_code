package com.tmz.aicode.core;

import com.tmz.aicode.model.enums.CodeGenTypeEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 使用真实模型验证“生成结构化代码并写入文件”的完整流程。
 *
 * 运行这个类会直接请求模型并产生额度消耗，适合需要人工确认真实生成效果时单独执行。
 */
@SpringBootTest
class AiCodeGeneratorFacadeIntegrationTest {

    /**
     * 注入正式的门面对象，其中连接了真实 AI 服务和本地文件保存器。
     */
    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;

    /**
     * 让真实模型生成单文件网站并保存，所有样式和交互代码都应包含在 index.html 中。
     *
     * 这个测试只要求输出目录中存在 index.html，同时确认没有额外生成独立的 CSS 和
     * JavaScript 文件。运行成功后可以直接双击 index.html 查看页面效果。
     */
    @Test
    void generateAndSaveHtmlCode() {
        File outputDirectory = aiCodeGeneratorFacade.generateAndSaveCode(
                "创建一个简洁的个人介绍网站，包含技能、项目经历和联系方式",
                CodeGenTypeEnum.HTML,
                1L
        );

        assertTrue(outputDirectory.isDirectory(), "生成结果目录应当存在");
        assertTrue(new File(outputDirectory, "index.html").isFile(), "index.html 应当存在");
        assertFalse(new File(outputDirectory, "style.css").exists(), "单文件模式不应生成 style.css");
        assertFalse(new File(outputDirectory, "script.js").exists(), "单文件模式不应生成 script.js");
        System.out.println("单文件模式生成目录：" + outputDirectory.getAbsolutePath());
    }

    /**
     * 让真实模型生成多文件网站并保存，成功后在控制台输出生成目录。
     *
     * 运行完成后可以打开目录中的 index.html 查看页面，同时检查 style.css
     * 和 script.js 是否已经正确写入。
    */
    @Test
    void generateAndSaveMultiFileCode() {
        File outputDirectory = aiCodeGeneratorFacade.generateAndSaveCode(
                "创建一个简洁的任务记录网站，支持添加、完成和筛选任务",
                CodeGenTypeEnum.MULTI_FILE,
                2L
        );

        assertTrue(outputDirectory.isDirectory(), "生成结果目录应当存在");
        assertTrue(new File(outputDirectory, "index.html").isFile(), "index.html 应当存在");
        assertTrue(new File(outputDirectory, "style.css").isFile(), "style.css 应当存在");
        assertTrue(new File(outputDirectory, "script.js").isFile(), "script.js 应当存在");
        System.out.println("多文件模式生成目录：" + outputDirectory.getAbsolutePath());
    }

    /**
     * 使用真实模型验证多文件流式生成。
     *
     * collectList 会订阅 Flux 并等待模型发送完成，门面随后解析全部片段并将文件写入磁盘。
     * 这个方法会产生真实模型费用，由需要检查实际流式效果时手动运行。
     */
    @Test
    void generateAndSaveCodeStream() {
        Flux<String> codeStream = aiCodeGeneratorFacade.generateAndSaveCodeStream(
                "创建一个简洁的任务记录网站，支持添加、完成和筛选任务",
                CodeGenTypeEnum.MULTI_FILE,
                3L
        );

        List<String> chunks = codeStream.collectList().block();
        assertNotNull(chunks, "模型流式返回结果不能为 null");
        String completeContent = String.join("", chunks);
        assertFalse(completeContent.isBlank(), "模型流式返回内容不能为空");
        System.out.println("本次共收到 " + chunks.size() + " 个代码片段");
    }
}
