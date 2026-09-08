package com.tmz.aicode.core;

import com.tmz.aicode.ai.model.HtmlCodeResult;
import com.tmz.aicode.ai.model.MultiFileCodeResult;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将完整的流式响应文本解析成可以交给文件保存器处理的结构化对象。
 *
 * 流式传输期间，每个片段可能只包含几个字符，单独解析没有意义。门面会先按顺序拼接全部
 * 片段，流正常结束后再调用这里的方法提取 Markdown 代码块。
 */
public final class CodeParser {

    /**
     * 匹配使用 html 语言标记的 Markdown 代码块，同时兼容 Windows 和 Unix 换行符。
     */
    private static final Pattern HTML_CODE_PATTERN = Pattern.compile(
            "```html[\\t ]*\\R([\\s\\S]*?)```",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * 匹配使用 css 语言标记的 Markdown 代码块。
     */
    private static final Pattern CSS_CODE_PATTERN = Pattern.compile(
            "```css[\\t ]*\\R([\\s\\S]*?)```",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * JavaScript 代码块允许使用 js 或 javascript 两种常见语言标记。
     */
    private static final Pattern JS_CODE_PATTERN = Pattern.compile(
            "```(?:js|javascript)[\\t ]*\\R([\\s\\S]*?)```",
            Pattern.CASE_INSENSITIVE
    );

    private CodeParser() {
        // 解析器没有实例状态，统一通过静态方法调用。
    }

    /**
     * 从完整响应中提取单文件 HTML。
     *
     * 如果模型没有添加 Markdown 代码块，方法会把整个响应作为 HTML 返回。这项回退处理
     * 可以容忍模型偶尔省略代码块标记，同时不会丢失已经生成的页面内容。
     *
     * @param codeContent 拼接完成的模型响应
     * @return 包含完整 HTML 代码的结构化结果
     */
    public static HtmlCodeResult parseHtmlCode(String codeContent) {
        Objects.requireNonNull(codeContent, "待解析的单文件代码不能为 null");
        HtmlCodeResult result = new HtmlCodeResult();
        String htmlCode = extractCodeByPattern(codeContent, HTML_CODE_PATTERN);
        result.setHtmlCode(htmlCode == null || htmlCode.isBlank()
                ? codeContent.trim()
                : htmlCode.trim());
        return result;
    }

    /**
     * 从完整响应中分别提取 HTML、CSS 和 JavaScript。
     *
     * 找不到的代码块会保持为 null，后续文件保存器会给出具体缺少哪个文件内容的错误，
     * 避免把不完整的生成结果当作成功结果写入磁盘。
     *
     * @param codeContent 拼接完成的模型响应
     * @return 分别保存三种代码的结构化结果
     */
    public static MultiFileCodeResult parseMultiFileCode(String codeContent) {
        Objects.requireNonNull(codeContent, "待解析的多文件代码不能为 null");
        MultiFileCodeResult result = new MultiFileCodeResult();
        result.setHtmlCode(trimToNull(extractCodeByPattern(codeContent, HTML_CODE_PATTERN)));
        result.setCssCode(trimToNull(extractCodeByPattern(codeContent, CSS_CODE_PATTERN)));
        result.setJsCode(trimToNull(extractCodeByPattern(codeContent, JS_CODE_PATTERN)));
        return result;
    }

    /**
     * 使用指定规则提取第一个代码块正文。
     *
     * @param content 完整的模型响应
     * @param pattern 当前代码类型对应的匹配规则
     * @return 匹配到的代码正文；没有匹配时返回 {@code null}
     */
    private static String extractCodeByPattern(String content, Pattern pattern) {
        Matcher matcher = pattern.matcher(content);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * 去除代码块首尾空白，并把空内容统一转换为 null。
     *
     * @param content 从代码块中提取出的原始内容
     * @return 清理后的代码；没有有效内容时返回 {@code null}
     */
    private static String trimToNull(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        return content.trim();
    }
}
