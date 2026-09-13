package com.tmz.aicode.core;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import com.tmz.aicode.ai.AiCodeGeneratorService;
import com.tmz.aicode.ai.AiCodeGeneratorServiceFactory;
import com.tmz.aicode.ai.model.HtmlCodeResult;
import com.tmz.aicode.ai.model.MultiFileCodeResult;
import com.tmz.aicode.ai.model.message.AiResponseMessage;
import com.tmz.aicode.ai.model.message.StreamMessageTypeEnum;
import com.tmz.aicode.model.enums.CodeGenTypeEnum;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.ToolExecution;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 使用本地 Demo 服务验证门面模式的生成与保存流程。
 *
 * Demo 服务直接返回固定的结构化对象，不会访问网络，也不会消耗任何模型额度。
 * 测试关注门面是否选择了正确的生成方法，以及文件保存器是否写出了预期文件。
 */
class AiCodeGeneratorFacadeTest {

    private static final File OUTPUT_ROOT = new File(
            System.getProperty("user.dir") + File.separator + "tmp" + File.separator + "code_output"
    );

    private final List<File> generatedDirectories = new ArrayList<>();
    private final AiCodeGeneratorService demoService = new DemoAiCodeGeneratorService();
    private final AiCodeGeneratorServiceFactory serviceFactory = mock(AiCodeGeneratorServiceFactory.class);
    private final AiCodeGeneratorFacade facade;

    /**
     * 工厂在单元测试中始终返回本地 Demo 服务。这样既能覆盖门面按 appId 获取服务的新流程，
     * 又不会连接 Redis 或向真实模型发送请求。
     */
    AiCodeGeneratorFacadeTest() {
        when(serviceFactory.getAiCodeGeneratorService(anyLong(), any(CodeGenTypeEnum.class)))
                .thenReturn(demoService);
        facade = new AiCodeGeneratorFacade(serviceFactory);
    }

    /**
     * 验证单文件模式只生成 index.html，并且文件内容与 Demo 返回值一致。
     */
    @Test
    void generateAndSaveHtmlCode() {
        long appId = IdUtil.getSnowflakeNextId();
        File outputDirectory = facade.generateAndSaveCode(
                "生成一个欢迎页面",
                CodeGenTypeEnum.HTML,
                appId
        );
        generatedDirectories.add(outputDirectory);

        assertEquals("html_" + appId, outputDirectory.getName());
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
        File outputDirectory = facade.generateAndSaveCode(
                "生成一个作品展示页面",
                CodeGenTypeEnum.MULTI_FILE,
                IdUtil.getSnowflakeNextId()
        );
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
     * 验证单文件流的所有片段会原样传给下游，并在完成后保存成 index.html。
     */
    @Test
    void generateAndSaveHtmlCodeStream() {
        Set<String> directoriesBeforeTest = currentOutputDirectoryNames();

        List<String> chunks = facade.generateAndSaveCodeStream(
                        "流式生成欢迎页面",
                        CodeGenTypeEnum.HTML,
                        IdUtil.getSnowflakeNextId()
                )
                .collectList()
                .block();

        assertNotNull(chunks, "流式结果不能为 null");
        assertEquals(DemoAiCodeGeneratorService.HTML_STREAM_CONTENT, String.join("", chunks));
        File outputDirectory = findNewOutputDirectory(directoriesBeforeTest);
        generatedDirectories.add(outputDirectory);
        assertEquals(DemoAiCodeGeneratorService.HTML_CODE,
                readGeneratedFile(outputDirectory, "index.html"));
    }

    /**
     * 验证多文件流结束后会被解析，并生成三个内容正确的代码文件。
     */
    @Test
    void generateAndSaveMultiFileCodeStream() {
        Set<String> directoriesBeforeTest = currentOutputDirectoryNames();

        List<String> chunks = facade.generateAndSaveCodeStream(
                        "流式生成作品展示页面",
                        CodeGenTypeEnum.MULTI_FILE,
                        IdUtil.getSnowflakeNextId()
                )
                .collectList()
                .block();

        assertNotNull(chunks, "流式结果不能为 null");
        assertEquals(DemoAiCodeGeneratorService.MULTI_FILE_STREAM_CONTENT, String.join("", chunks));
        File outputDirectory = findNewOutputDirectory(directoriesBeforeTest);
        generatedDirectories.add(outputDirectory);
        assertEquals(DemoAiCodeGeneratorService.HTML_CODE,
                readGeneratedFile(outputDirectory, "index.html"));
        assertEquals(DemoAiCodeGeneratorService.CSS_CODE,
                readGeneratedFile(outputDirectory, "style.css"));
        assertEquals(DemoAiCodeGeneratorService.JS_CODE,
                readGeneratedFile(outputDirectory, "script.js"));
    }

    /**
     * 普通对话也应完整传给调用方，但不能因为没有 HTML 代码而创建或覆盖网站文件。
     * 该测试使用固定本地回复，不会调用真实模型。
     */
    @Test
    void casualConversationCompletesWithoutSavingWebsiteFiles() {
        Set<String> directoriesBeforeTest = currentOutputDirectoryNames();

        List<String> chunks = facade.generateAndSaveCodeStream(
                        "你是谁",
                        CodeGenTypeEnum.MULTI_FILE,
                        IdUtil.getSnowflakeNextId()
                )
                .collectList()
                .block();

        assertNotNull(chunks);
        assertEquals(DemoAiCodeGeneratorService.CASUAL_RESPONSE, String.join("", chunks));
        assertEquals(directoriesBeforeTest, currentOutputDirectoryNames(),
                "闲聊回复不应创建网站目录");
    }

    /**
     * Vue 模式应选择带文件工具的专用服务，并把 TokenStream 文本事件转换成统一 JSON。
     */
    @Test
    void generateVueProjectCodeStream() {
        long appId = IdUtil.getSnowflakeNextId();

        List<String> chunks = facade.generateAndSaveCodeStream(
                        "生成一个任务管理 Vue 项目",
                        CodeGenTypeEnum.VUE_PROJECT,
                        appId
                )
                .collectList()
                .block();

        assertNotNull(chunks);
        List<AiResponseMessage> messages = chunks.stream()
                .map(chunk -> JSONUtil.toBean(chunk, AiResponseMessage.class))
                .toList();
        assertEquals(List.of(
                "开始创建 Vue 工程。",
                "package.json 写入成功。",
                "Vue 工程生成完成。"
        ), messages.stream().map(AiResponseMessage::getData).toList());
        assertTrue(messages.stream().allMatch(message ->
                StreamMessageTypeEnum.AI_RESPONSE.getValue().equals(message.getType())));
        verify(serviceFactory).getAiCodeGeneratorService(appId, CodeGenTypeEnum.VUE_PROJECT);
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
     * 记录测试开始前已经存在的输出目录，避免误把用户此前生成的网站当成当前测试结果。
     *
     * @return 当前输出根目录下的目录名称集合
     */
    private Set<String> currentOutputDirectoryNames() {
        File[] directories = OUTPUT_ROOT.listFiles(File::isDirectory);
        if (directories == null) {
            return Set.of();
        }
        return Arrays.stream(directories)
                .map(File::getName)
                .collect(Collectors.toSet());
    }

    /**
     * 找到订阅流之后新创建的唯一目录。
     *
     * @param directoriesBeforeTest 测试开始前的目录名称
     * @return 当前流式生成创建的目录
     */
    private File findNewOutputDirectory(Set<String> directoriesBeforeTest) {
        File[] newDirectories = OUTPUT_ROOT.listFiles(file ->
                file.isDirectory() && !directoriesBeforeTest.contains(file.getName()));
        assertNotNull(newDirectories, "输出根目录应当存在");
        assertEquals(1, newDirectories.length, "一次流式生成应创建一个独立目录");
        return newDirectories[0];
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
        private static final String CASUAL_RESPONSE = "我是灵构 AI，可以继续帮你修改网站。";
        private static final String HTML_STREAM_CONTENT =
                "```html\n" + HTML_CODE + "\n```";
        private static final String MULTI_FILE_STREAM_CONTENT =
                "```html\n" + HTML_CODE + "\n```\n"
                        + "```css\n" + CSS_CODE + "\n```\n"
                        + "```javascript\n" + JS_CODE + "\n```";
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

        /**
         * 把单文件响应拆成多个片段，用来模拟模型逐段返回代码的过程。
         */
        @Override
        public Flux<String> generateHtmlCodeStream(String userMessage) {
            return Flux.just("```html\n", HTML_CODE, "\n```");
        }

        /**
         * 按 HTML、CSS、JavaScript 的顺序发送多个片段，模拟多文件流式响应。
         */
        @Override
        public Flux<String> generateMultiFileCodeStream(String userMessage) {
            if ("你是谁".equals(userMessage)) {
                return Flux.just("我是灵构 AI，", "可以继续帮你修改网站。");
            }
            return Flux.just(
                    "```html\n", HTML_CODE, "\n```\n",
                    "```css\n", CSS_CODE, "\n```\n",
                    "```javascript\n", JS_CODE, "\n```"
            );
        }

        /**
         * 模拟 Vue 模型通过 TokenStream 依次发送规划文本和完成提示。
         */
        @Override
        public TokenStream generateVueProjectCodeStream(long appId, String userMessage) {
            return new DemoTokenStream(List.of(
                    "开始创建 Vue 工程。",
                    "package.json 写入成功。",
                    "Vue 工程生成完成。"
            ));
        }
    }

    /**
     * 可控的本地 TokenStream，只发送测试给定的文本，不创建网络连接或执行文件工具。
     */
    private static class DemoTokenStream implements TokenStream {

        private final List<String> partialResponses;

        private Consumer<String> partialResponseHandler;

        private Consumer<ChatResponse> completeResponseHandler;

        private Consumer<Throwable> errorHandler;

        DemoTokenStream(List<String> partialResponses) {
            this.partialResponses = partialResponses;
        }

        @Override
        public TokenStream onPartialResponse(Consumer<String> partialResponseHandler) {
            this.partialResponseHandler = partialResponseHandler;
            return this;
        }

        @Override
        public TokenStream onPartialToolExecutionRequest(
                BiConsumer<Integer, ToolExecutionRequest> toolExecutionRequestHandler) {
            return this;
        }

        @Override
        public TokenStream onCompleteToolExecutionRequest(
                BiConsumer<Integer, ToolExecutionRequest> completedHandler) {
            return this;
        }

        @Override
        public TokenStream onRetrieved(Consumer<List<Content>> contentHandler) {
            return this;
        }

        @Override
        public TokenStream onToolExecuted(Consumer<ToolExecution> toolExecuteHandler) {
            return this;
        }

        @Override
        public TokenStream onCompleteResponse(Consumer<ChatResponse> completeResponseHandler) {
            this.completeResponseHandler = completeResponseHandler;
            return this;
        }

        @Override
        public TokenStream onError(Consumer<Throwable> errorHandler) {
            this.errorHandler = errorHandler;
            return this;
        }

        @Override
        public TokenStream ignoreErrors() {
            this.errorHandler = null;
            return this;
        }

        /**
         * 按给定顺序触发文本回调，随后发出完成信号。
         */
        @Override
        public void start() {
            try {
                partialResponses.forEach(partialResponseHandler);
                completeResponseHandler.accept(null);
            } catch (Throwable error) {
                if (errorHandler != null) {
                    errorHandler.accept(error);
                    return;
                }
                throw error;
            }
        }
    }
}
