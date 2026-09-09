package com.tmz.aicode.core.parser;

import com.tmz.aicode.ai.model.HtmlCodeResult;
import com.tmz.aicode.ai.model.MultiFileCodeResult;
import com.tmz.aicode.model.enums.CodeGenTypeEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * 验证解析执行器能够根据生成类型选择正确策略。
 *
 * 测试数据全部在本地构造，不会启动 Spring，也不会访问模型服务。
 */
class CodeParserExecutorTest {

    /**
     * HTML 类型应选择单文件策略，并去掉 Markdown 代码块标记。
     */
    @Test
    void executeHtmlParser() {
        String response = """
                ```html
                <!DOCTYPE html>
                <html><body><h1>欢迎</h1></body></html>
                ```
                """;

        Object parsedResult = CodeParserExecutor.executeParser(response, CodeGenTypeEnum.HTML);
        HtmlCodeResult result = assertInstanceOf(HtmlCodeResult.class, parsedResult);

        assertEquals("<!DOCTYPE html>\n<html><body><h1>欢迎</h1></body></html>",
                result.getHtmlCode());
    }

    /**
     * MULTI_FILE 类型应选择多文件策略，并分别填充三个代码字段。
     */
    @Test
    void executeMultiFileParser() {
        String response = """
                ```html
                <!DOCTYPE html><html><body><h1>作品</h1></body></html>
                ```
                ```css
                h1 { color: #2563eb; }
                ```
                ```javascript
                console.log('页面加载完成');
                ```
                """;

        Object parsedResult = CodeParserExecutor.executeParser(response, CodeGenTypeEnum.MULTI_FILE);
        MultiFileCodeResult result = assertInstanceOf(MultiFileCodeResult.class, parsedResult);

        assertEquals("<!DOCTYPE html><html><body><h1>作品</h1></body></html>", result.getHtmlCode());
        assertEquals("h1 { color: #2563eb; }", result.getCssCode());
        assertEquals("console.log('页面加载完成');", result.getJsCode());
    }
}
