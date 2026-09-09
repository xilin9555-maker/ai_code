package com.tmz.aicode.core.parser;

import com.tmz.aicode.exception.BusinessException;
import com.tmz.aicode.exception.ErrorCode;
import com.tmz.aicode.model.enums.CodeGenTypeEnum;

/**
 * 代码解析策略的统一执行入口。
 *
 * 门面只需要提供完整代码和生成类型，执行器负责选择具体策略。不同策略虽然返回不同对象，
 * 仍然可以通过 Object 统一交给后续的文件保存执行器。
 */
public final class CodeParserExecutor {

    private static final HtmlCodeParser HTML_CODE_PARSER = new HtmlCodeParser();
    private static final MultiFileCodeParser MULTI_FILE_CODE_PARSER = new MultiFileCodeParser();

    private CodeParserExecutor() {
        // 执行器持有可复用的无状态策略，不需要创建实例。
    }

    /**
     * 根据生成类型选择解析策略。
     *
     * @param codeContent 已完成拼接的模型响应
     * @param codeGenType 代码生成类型
     * @return HtmlCodeResult 或 MultiFileCodeResult
     */
    public static Object executeParser(String codeContent, CodeGenTypeEnum codeGenType) {
        if (codeGenType == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "代码生成类型不能为空");
        }
        return switch (codeGenType) {
            case HTML -> HTML_CODE_PARSER.parseCode(codeContent);
            case MULTI_FILE -> MULTI_FILE_CODE_PARSER.parseCode(codeContent);
        };
    }
}
