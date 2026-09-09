package com.tmz.aicode.core.parser;

import com.tmz.aicode.ai.model.HtmlCodeResult;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 单文件 HTML 的解析策略。
 *
 * 该策略只关心 html 代码块，解析细节与多文件模式相互独立。以后单文件输出格式发生变化时，
 * 只需要修改这个类，不会影响其他生成方式。
 */
public class HtmlCodeParser implements CodeParser<HtmlCodeResult> {

    /**
     * 匹配 html Markdown 代码块，并兼容 Windows 和 Unix 换行符。
     */
    private static final Pattern HTML_CODE_PATTERN = Pattern.compile(
            "```html[\\t ]*\\R([\\s\\S]*?)```",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * 提取单文件 HTML。模型偶尔省略代码块标记时，会将完整响应作为 HTML 返回，
     * 让已经生成的页面内容仍然能够进入后续保存流程。
     *
     * @param codeContent 已完成拼接的模型响应
     * @return 包含 HTML 代码的结构化结果
     */
    @Override
    public HtmlCodeResult parseCode(String codeContent) {
        Objects.requireNonNull(codeContent, "待解析的单文件代码不能为 null");
        String htmlCode = extractHtmlCode(codeContent);

        HtmlCodeResult result = new HtmlCodeResult();
        result.setHtmlCode(htmlCode == null || htmlCode.isBlank()
                ? codeContent.trim()
                : htmlCode.trim());
        return result;
    }

    /**
     * 从响应中提取第一个 html 代码块正文。
     *
     * @param content 完整的模型响应
     * @return 匹配到的 HTML；没有代码块时返回 {@code null}
     */
    private String extractHtmlCode(String content) {
        Matcher matcher = HTML_CODE_PATTERN.matcher(content);
        return matcher.find() ? matcher.group(1) : null;
    }
}
