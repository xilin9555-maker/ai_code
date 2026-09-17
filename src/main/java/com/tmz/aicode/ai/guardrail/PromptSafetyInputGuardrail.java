package com.tmz.aicode.ai.guardrail;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 在用户消息进入模型之前执行基础安全审查。
 *
 * 该护轨只负责确定性、本地化的快速检查，不访问模型或外部内容审核服务。检查不通过时
 * 返回 fatal 结果，LangChain4j 会立即终止当前调用，因此被拒绝的消息不会进入模型上下文，
 * 也不会消耗模型额度。
 */
public class PromptSafetyInputGuardrail implements InputGuardrail {

    /** 单次用户输入允许的最大字符数，防止异常长文本占用过多上下文。 */
    public static final int MAX_PROMPT_LENGTH = 1000;

    /**
     * 基础敏感词列表同时覆盖中英文常见表达。
     *
     * 列表保持不可变，护轨实例可以安全地被多个 AI Service 共享使用。英文匹配前会统一
     * 转成小写，避免通过大小写变化绕过检查。
     */
    private static final List<String> SENSITIVE_WORDS = List.of(
            "忽略之前的指令",
            "ignore previous instructions",
            "ignore above",
            "破解",
            "hack",
            "绕过",
            "bypass",
            "越狱",
            "jailbreak"
    );

    /**
     * 常见指令注入结构。
     *
     * 正则使用不区分大小写模式，并允许单词之间出现不同数量的空白字符，从而识别简单的
     * 大小写或空格变形。静态预编译可以避免每次请求重复解析表达式。
     */
    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            Pattern.compile(
                    "(?i)ignore\\s+(?:previous|above|all)\\s+"
                            + "(?:instructions?|commands?|prompts?)"
            ),
            Pattern.compile(
                    "(?i)(?:forget|disregard)\\s+(?:everything|all)\\s+(?:above|before)"
            ),
            Pattern.compile(
                    "(?i)(?:pretend|act|behave)\\s+(?:as|like)\\s+(?:if|you\\s+are)"
            ),
            Pattern.compile("(?i)system\\s*:\\s*you\\s+are"),
            Pattern.compile("(?i)new\\s+(?:instructions?|commands?|prompts?)\\s*:")
    );

    /**
     * 按固定顺序检查消息格式、长度、敏感词和指令注入结构。
     *
     * @param userMessage LangChain4j 已组装的用户消息
     * @return success 表示允许继续调用模型，fatal 表示立即拒绝本次调用
     */
    @Override
    public InputGuardrailResult validate(UserMessage userMessage) {
        if (userMessage == null || !userMessage.hasSingleText()) {
            return fatal("输入内容必须是单段文本");
        }

        String input = userMessage.singleText();
        if (input.length() > MAX_PROMPT_LENGTH) {
            return fatal("输入内容过长，不要超过 " + MAX_PROMPT_LENGTH + " 字");
        }
        if (input.trim().isEmpty()) {
            return fatal("输入内容不能为空");
        }

        String lowerInput = input.toLowerCase(Locale.ROOT);
        for (String sensitiveWord : SENSITIVE_WORDS) {
            if (lowerInput.contains(sensitiveWord.toLowerCase(Locale.ROOT))) {
                return fatal("输入包含不当内容，请修改后重试");
            }
        }

        for (Pattern injectionPattern : INJECTION_PATTERNS) {
            if (injectionPattern.matcher(input).find()) {
                return fatal("检测到恶意输入，请求被拒绝");
            }
        }
        return success();
    }
}
