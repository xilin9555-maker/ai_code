package com.tmz.aicode.core.saver;

import cn.hutool.core.util.StrUtil;
import com.tmz.aicode.ai.model.MultiFileCodeResult;
import com.tmz.aicode.exception.BusinessException;
import com.tmz.aicode.exception.ErrorCode;
import com.tmz.aicode.model.enums.CodeGenTypeEnum;

/**
 * HTML、CSS 和 JavaScript 多文件模式的保存模板实现。
 */
public class MultiFileCodeFileSaverTemplate extends CodeFileSaverTemplate<MultiFileCodeResult> {

    @Override
    protected CodeGenTypeEnum getCodeType() {
        return CodeGenTypeEnum.MULTI_FILE;
    }

    /**
     * 将三个代码字段写入约定的文件。CSS 和 JavaScript 可以为空，父类会自动跳过空内容；
     * HTML 是页面入口，必须通过 validateInput 校验后才能进入这里。
     *
     * @param result 多文件结构化代码结果
     * @param baseDirPath 已创建的独立输出目录
     */
    @Override
    protected void saveFiles(MultiFileCodeResult result, String baseDirPath) {
        writeToFile(baseDirPath, "index.html", result.getHtmlCode());
        writeToFile(baseDirPath, "style.css", result.getCssCode());
        writeToFile(baseDirPath, "script.js", result.getJsCode());
    }

    /**
     * 多文件模式至少要有 HTML 入口文件，CSS 和 JavaScript 根据页面需求可以为空。
     *
     * @param result 多文件结构化代码结果
     */
    @Override
    protected void validateInput(MultiFileCodeResult result) {
        super.validateInput(result);
        if (StrUtil.isBlank(result.getHtmlCode())) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "HTML 代码内容不能为空");
        }
    }
}
