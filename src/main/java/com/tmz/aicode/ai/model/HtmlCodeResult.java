package com.tmz.aicode.ai.model;

import dev.langchain4j.model.output.structured.Description;
import lombok.Data;

/**
 * 保存单文件网页的结构化生成结果。
 *
 * LangChain4j 会根据字段类型和 Description 注解组织输出要求，并把模型返回的 JSON
 * 转换成这个对象。业务代码可以直接读取字段，不需要再从一整段文本中查找 HTML。
 */
@Data
@Description("单文件 HTML 网页的生成结果")
public class HtmlCodeResult {

    /**
     * 可以直接保存为 index.html 并在浏览器中运行的完整代码。
     */
    @Description("完整的 HTML 文档代码，包含页面结构、CSS 样式和 JavaScript 交互")
    private String htmlCode;

    /**
     * 对页面内容、主要功能和设计思路的简短说明。
     */
    @Description("对生成页面的功能和设计特点进行简要说明")
    private String description;
}
