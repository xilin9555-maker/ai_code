package com.tmz.aicode.ai.model;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

/**
 * 保存多文件网页的结构化生成结果。
 *
 * HTML、CSS 和 JavaScript 分别存放，后续可以直接将三个字段写入对应文件，
 * 不需要依赖 Markdown 标记拆分模型返回内容。
 */
@Data
@Description("由 HTML、CSS 和 JavaScript 组成的多文件网页生成结果")
public class MultiFileCodeResult {

    /**
     * 页面结构代码，其中会引用 style.css 和 script.js。
     */
    @Description("index.html 的完整代码，正确引用 style.css 和 script.js")
    private String htmlCode;

    /**
     * 页面全部样式代码，可以直接保存为 style.css。
     */
    @Description("style.css 的完整 CSS 样式代码")
    private String cssCode;

    /**
     * 页面全部交互逻辑，可以直接保存为 script.js。
     */
    @Description("script.js 的完整原生 JavaScript 代码")
    private String jsCode;

    /**
     * 对页面功能、文件关系和设计特点的简短说明。
     */
    @Description("对生成页面的功能和设计特点进行简要说明")
    private String description;
}
