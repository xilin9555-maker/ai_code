package com.tmz.aicode.ai;

import com.tmz.aicode.ai.model.HtmlCodeResult;
import com.tmz.aicode.ai.model.MultiFileCodeResult;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 使用当前环境配置的真实大模型验证网页代码生成服务。
 *
 * 测试启动完整的 Spring Boot 上下文，从容器中取得 AiCodeGeneratorService。
 * 调用方法时会读取系统提示词并访问模型服务，因此运行测试需要有效的网络和 API Key，
 * 每次执行也会产生真实的模型请求和额度消耗。
 */
@SpringBootTest
class AiCodeGeneratorServiceTest {

    /**
     * Spring 注入由 AiCodeGeneratorServiceFactory 创建的服务代理。
     * 代理对象内部连接的是 application_local.yml 中配置的 ChatModel。
     */
    @Resource
    private AiCodeGeneratorService aiCodeGeneratorService;

    /**
     * 请求模型生成可以直接运行的单文件 HTML 页面。
     *
     * 除了检查结果对象，还会确认 HTML 字段已经由 LangChain4j 从 JSON 中解析出来。
     */
    @Test
    void generateHtmlCode() {
        HtmlCodeResult result = aiCodeGeneratorService.generateHtmlCode(
                "创建一个简洁的个人记账网站，支持添加收支记录并展示本月结余"
        );

        assertNotNull(result, "模型返回结果不能为 null");
        assertCodeNotBlank(result.getHtmlCode(), "HTML 代码不能为空");
        System.out.println("单文件结构化生成结果：\n" + result);
    }

    /**
     * 请求模型分别生成 HTML、CSS 和 JavaScript，验证多文件模式可以取得有效结果。
     */
    @Test
    void generateMultiFileCode() {
        MultiFileCodeResult result = aiCodeGeneratorService.generateMultiFileCode(
                "创建一个作品展示网站，包含项目卡片、分类筛选和联系表单"
        );

        assertNotNull(result, "模型返回结果不能为 null");
        assertCodeNotBlank(result.getHtmlCode(), "HTML 代码不能为空");
        assertCodeNotBlank(result.getCssCode(), "CSS 代码不能为空");
        assertCodeNotBlank(result.getJsCode(), "JavaScript 代码不能为空");
        System.out.println("多文件结构化生成结果：\n" + result);
    }

    /**
     * 检查结构化结果中的代码字段是否含有可继续处理的内容。
     *
     * @param code 模型生成并由 LangChain4j 解析后的代码字段
     * @param message 字段为空时显示的断言提示
     */
    private void assertCodeNotBlank(String code, String message) {
        assertNotNull(code, message);
        assertFalse(code.isBlank(), message);
    }
}
