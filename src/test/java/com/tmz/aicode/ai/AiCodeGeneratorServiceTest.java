package com.tmz.aicode.ai;

import cn.hutool.core.util.IdUtil;
import com.tmz.aicode.ai.model.HtmlCodeResult;
import com.tmz.aicode.ai.model.MultiFileCodeResult;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
     * 工厂可以按应用 id 创建绑定独立 Redis 记忆的服务，用它可以验证跨实例恢复上下文。
     */
    @Resource
    private AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;

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
     * 使用真实模型验证同一个应用的对话记忆。
     *
     * 第一次请求要求页面包含唯一标记，完成后 LangChain4j 会把用户消息和 AI 回复保存到
     * Redis。第二次按相同 appId 获取服务会命中 Caffeine，本轮重点验证缓存接入后模型仍能
     * 从绑定的 Redis 记忆中取得上一轮内容，并继续完成增量修改。
     *
     * 该测试会发送两次真实模型请求并产生额度消耗。测试使用新的雪花 id 隔离本次记忆，
     * 控制台会输出 id，便于运行后在 Redis 中检查对应 Key 和剩余过期时间。
     */
    @Test
    void chatMemoryKeepsContextForSameApplication() {
        long appId = IdUtil.getSnowflakeNextId();
        String memoryMarker = "MEMORY_ALPHA_7341";
        System.out.println("会话记忆测试 appId：" + appId);

        AiCodeGeneratorService firstService =
                aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId);
        HtmlCodeResult firstResult = firstService.generateHtmlCode(
                "创建一个只有标题的极简网页，标题文字必须是 " + memoryMarker
                        + "，总代码不超过 30 行"
        );
        assertNotNull(firstResult, "第一次模型响应不能为 null");
        assertCodeNotBlank(firstResult.getHtmlCode(), "第一次生成的 HTML 不能为空");
        assertTrue(firstResult.getHtmlCode().contains(memoryMarker),
                "第一次生成结果应包含指定的记忆标记");

        // 再次按相同 appId 获取服务会命中本地缓存，服务内部继续使用相同的 Redis 记忆。
        AiCodeGeneratorService secondService =
                aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId);
        HtmlCodeResult secondResult = secondService.generateHtmlCode(
                "继续修改刚才的网站：保留原来的标题文字，并在标题下增加“记忆已生效”，"
                        + "总代码不超过 40 行"
        );
        assertNotNull(secondResult, "第二次模型响应不能为 null");
        assertCodeNotBlank(secondResult.getHtmlCode(), "第二次生成的 HTML 不能为空");
        assertTrue(secondResult.getHtmlCode().contains(memoryMarker),
                "第二次生成结果应从 Redis 记忆中恢复第一次的标题标记");
        assertTrue(secondResult.getHtmlCode().contains("记忆已生效"),
                "第二次生成结果应完成本轮追加的修改");

        System.out.println("第二次生成结果：\n" + secondResult);
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
