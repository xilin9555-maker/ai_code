package com.tmz.aicode.ai;

import com.tmz.aicode.ai.model.HtmlCodeResult;
import com.tmz.aicode.ai.model.MultiFileCodeResult;
import dev.langchain4j.service.SystemMessage;
import reactor.core.publisher.Flux;

/**
 * 调用大模型生成网页代码的服务。
 *
 * LangChain4j 会在运行时为这个接口创建代理对象。业务代码只需要调用普通 Java
 * 方法，代理对象会负责组合系统提示词和用户消息、请求大模型，并把 JSON 响应转换成
 * 对应的 Java 结果对象。
 */
public interface AiCodeGeneratorService {

    /**
     * 生成可以独立运行的单文件 HTML 页面。
     *
     * 这种模式会把页面结构、样式和交互脚本全部放进同一个 HTML 文件，适合快速预览
     * 或生成结构较简单的网站。
     *
     * @param userMessage 用户对网站功能、内容和视觉效果的自然语言描述
     * @return 包含完整 HTML 代码和页面说明的结构化结果
     */
    @SystemMessage(fromResource = "prompt/codegen-html-system-prompt.txt")
    HtmlCodeResult generateHtmlCode(String userMessage);

    /**
     * 分别生成 HTML、CSS 和 JavaScript 三部分代码。
     *
     * 这种模式便于后续解析并保存成多个文件，也更适合继续维护功能较多的网站。
     *
     * @param userMessage 用户对网站功能、内容和视觉效果的自然语言描述
     * @return 分别包含 HTML、CSS、JavaScript 和页面说明的结构化结果
     */
    @SystemMessage(fromResource = "prompt/codegen-multi-file-system-prompt.txt")
    MultiFileCodeResult generateMultiFileCode(String userMessage);

    /**
     * 以流式方式生成单文件 HTML 页面。
     *
     * 每当模型生成一小段内容，Flux 就会向下游发送一个字符串片段。调用方可以立即把片段
     * 推送给前端，不需要等完整网页全部生成后再响应。
     *
     * @param userMessage 用户对网站功能、内容和视觉效果的自然语言描述
     * @return 按生成顺序持续发出代码片段的数据流
     */
    @SystemMessage(fromResource = "prompt/codegen-html-stream-system-prompt.txt")
    Flux<String> generateHtmlCodeStream(String userMessage);

    /**
     * 以流式方式生成 HTML、CSS 和 JavaScript 三个文件的代码。
     *
     * 流中的内容仍保持 Markdown 代码块格式，全部接收完成后可以交给 CodeParser
     * 拆分成结构化对象，再分别保存为三个文件。
     *
     * @param userMessage 用户对网站功能、内容和视觉效果的自然语言描述
     * @return 按生成顺序持续发出多文件代码片段的数据流
     */
    @SystemMessage(fromResource = "prompt/codegen-multi-file-stream-system-prompt.txt")
    Flux<String> generateMultiFileCodeStream(String userMessage);
}
