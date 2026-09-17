package com.tmz.aicode.ai.guardrail;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 检查模型最终响应，并在内容明显无效时要求模型重新生成。
 *
 * 输出护轨运行在单次模型响应完成之后。返回 reprompt 时，LangChain4j 会把补充要求加入
 * 当前调用上下文并再次请求模型；只有通过检查的最终内容才会交给下游解析和文件保存。
 */
public class RetryOutputGuardrail implements OutputGuardrail {

    /** 低于该长度的回复通常是中断响应或无效确认语，不足以构成网页代码。 */
    public static final int MIN_RESPONSE_LENGTH = 10;

    /**
     * 可能代表真实凭据泄漏的内容模式。
     *
     * 这里不直接匹配 password、token 等单个词，因为登录表单中的字段名属于正常代码。
     * 只有出现疑似赋值、授权头或私钥正文时才触发重试，降低合法页面被误判的概率。
     */
    private static final List<Pattern> SENSITIVE_CONTENT_PATTERNS = List.of(
            Pattern.compile(
                    "(?i)(?:password|secret|token|api[ _-]?key|credential)"
                            + "\\s*[:=]\\s*[\"'][^\"'\\r\\n]{8,}[\"']"
            ),
            Pattern.compile(
                    "(?:密码|密钥|令牌|凭据)\\s*[:：=]\\s*[^\\s\"']{6,}"
            ),
            Pattern.compile(
                    "(?i)authorization\\s*[:=]\\s*[\"']?bearer\\s+"
                            + "[a-z0-9._-]{12,}"
            ),
            Pattern.compile(
                    "(?i)-----BEGIN(?: [A-Z]+)? PRIVATE KEY-----"
            )
    );

    /**
     * 校验模型返回的最终文本。
     *
     * @param responseFromLLM 模型本轮产生的最终消息
     * @return success 表示允许下游继续处理，reprompt 表示附加修正要求后重新调用模型
     */
    @Override
    public OutputGuardrailResult validate(AiMessage responseFromLLM) {
        if (responseFromLLM == null) {
            return reprompt("响应内容为空", "请重新生成完整的内容");
        }

        String response = responseFromLLM.text();
        if (response == null || response.trim().isEmpty()) {
            return reprompt("响应内容为空", "请重新生成完整的内容");
        }
        if (response.trim().length() < MIN_RESPONSE_LENGTH) {
            return reprompt("响应内容过短", "请提供更详细、完整且可以直接使用的内容");
        }
        if (containsSensitiveContent(response)) {
            return reprompt(
                    "响应包含疑似敏感凭据",
                    "请重新生成内容，移除密码、密钥、令牌、私钥等敏感凭据"
            );
        }
        /*
         * 显式携带本轮已经通过检查的文本。当前框架在发生 reprompt 后会重新调用模型，
         * 但普通 success 结果不保存重试得到的新响应；successWith 能让同步和流式调用都
         * 把最后一次通过检查的内容交给下游，而不是误用首次未通过的内容。
         */
        return successWith(response);
    }

    /**
     * 检查响应中是否出现疑似硬编码凭据或私钥正文。
     *
     * @param response 完整模型响应
     * @return 命中任一敏感内容模式时返回 true
     */
    private boolean containsSensitiveContent(String response) {
        for (Pattern pattern : SENSITIVE_CONTENT_PATTERNS) {
            if (pattern.matcher(response).find()) {
                return true;
            }
        }
        return false;
    }
}
