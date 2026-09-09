package com.tmz.aicode.core.parser;

import com.tmz.aicode.ai.model.MultiFileCodeResult;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTML、CSS 和 JavaScript 多文件代码的解析策略。
 *
 * 该策略按照代码块的语言标记分别提取三种内容，并封装成 MultiFileCodeResult。
 */
public class MultiFileCodeParser implements CodeParser<MultiFileCodeResult> {

    private static final Pattern HTML_CODE_PATTERN = Pattern.compile(
            "```html[\\t ]*\\R([\\s\\S]*?)```",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern CSS_CODE_PATTERN = Pattern.compile(
            "```css[\\t ]*\\R([\\s\\S]*?)```",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * JavaScript 代码块允许使用 js 和 javascript 两种常见语言标记。
     */
    private static final Pattern JS_CODE_PATTERN = Pattern.compile(
            "```(?:js|javascript)[\\t ]*\\R([\\s\\S]*?)```",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * 将完整响应拆分为三个独立代码字段。没有匹配到的代码块保持为 null，
     * 文件保存策略会根据当前模式决定哪些字段必须存在。
     *
     * @param codeContent 已完成拼接的模型响应
     * @return 分别包含 HTML、CSS 和 JavaScript 的结构化结果
     */
    @Override
    public MultiFileCodeResult parseCode(String codeContent) {
        Objects.requireNonNull(codeContent, "待解析的多文件代码不能为 null");

        MultiFileCodeResult result = new MultiFileCodeResult();
        result.setHtmlCode(trimToNull(extractCodeByPattern(codeContent, HTML_CODE_PATTERN)));
        result.setCssCode(trimToNull(extractCodeByPattern(codeContent, CSS_CODE_PATTERN)));
        result.setJsCode(trimToNull(extractCodeByPattern(codeContent, JS_CODE_PATTERN)));
        return result;
    }

    /**
     * 按指定规则提取第一个匹配的代码块正文。
     *
     * @param content 完整的模型响应
     * @param pattern 当前代码类型对应的匹配规则
     * @return 匹配到的代码；没有匹配时返回 {@code null}
     */
    private String extractCodeByPattern(String content, Pattern pattern) {
        Matcher matcher = pattern.matcher(content);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * 去除代码首尾空白，并将空字符串统一转换为 null。
     *
     * @param content 从代码块中提取的原始内容
     * @return 清理后的代码；没有有效内容时返回 {@code null}
     */
    private String trimToNull(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        return content.trim();
    }
}
