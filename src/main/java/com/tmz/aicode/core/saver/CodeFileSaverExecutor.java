package com.tmz.aicode.core.saver;

import com.tmz.aicode.ai.model.HtmlCodeResult;
import com.tmz.aicode.ai.model.MultiFileCodeResult;
import com.tmz.aicode.exception.BusinessException;
import com.tmz.aicode.exception.ErrorCode;
import com.tmz.aicode.model.enums.CodeGenTypeEnum;

import java.io.File;

/**
 * 文件保存模板的统一执行入口。
 *
 * 执行器根据生成类型完成结果类型检查，再调用对应模板。门面不需要了解每种模式写几个文件，
 * 也不需要进行不安全的强制类型转换。
 */
public final class CodeFileSaverExecutor {

    private static final HtmlCodeFileSaverTemplate HTML_CODE_FILE_SAVER =
            new HtmlCodeFileSaverTemplate();
    private static final MultiFileCodeFileSaverTemplate MULTI_FILE_CODE_FILE_SAVER =
            new MultiFileCodeFileSaverTemplate();

    private CodeFileSaverExecutor() {
        // 执行器持有可复用的无状态模板，不需要创建实例。
    }

    /**
     * 根据生成类型选择文件保存模板。
     *
     * @param codeResult 解析或结构化输出得到的代码结果对象
     * @param codeGenType 代码生成类型
     * @param appId 代码所属的应用 id
     * @return 当前应用对应的代码目录
     */
    public static File executeSaver(Object codeResult, CodeGenTypeEnum codeGenType, Long appId) {
        if (codeGenType == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "代码生成类型不能为空");
        }
        return switch (codeGenType) {
            case HTML -> saveHtmlResult(codeResult, appId);
            case MULTI_FILE -> saveMultiFileResult(codeResult, appId);
            case VUE_PROJECT -> throw new BusinessException(
                    ErrorCode.SYSTEM_ERROR,
                    "Vue 工程由文件工具直接写入，不需要保存器重复处理"
            );
        };
    }

    /**
     * 确认结果类型后执行单文件保存模板。
     */
    private static File saveHtmlResult(Object codeResult, Long appId) {
        if (!(codeResult instanceof HtmlCodeResult htmlCodeResult)) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "单文件模式的代码结果类型不正确");
        }
        return HTML_CODE_FILE_SAVER.saveCode(htmlCodeResult, appId);
    }

    /**
     * 确认结果类型后执行多文件保存模板。
     */
    private static File saveMultiFileResult(Object codeResult, Long appId) {
        if (!(codeResult instanceof MultiFileCodeResult multiFileCodeResult)) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "多文件模式的代码结果类型不正确");
        }
        return MULTI_FILE_CODE_FILE_SAVER.saveCode(multiFileCodeResult, appId);
    }
}
