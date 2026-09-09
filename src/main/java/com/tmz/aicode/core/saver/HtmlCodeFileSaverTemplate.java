package com.tmz.aicode.core.saver;

import cn.hutool.core.util.StrUtil;
import com.tmz.aicode.ai.model.HtmlCodeResult;
import com.tmz.aicode.exception.BusinessException;
import com.tmz.aicode.exception.ErrorCode;
import com.tmz.aicode.model.enums.CodeGenTypeEnum;

/**
 * 单文件 HTML 的保存模板实现。
 *
 * 该模式只创建 index.html，页面结构、样式和交互脚本都包含在这个文件中。
 */
public class HtmlCodeFileSaverTemplate extends CodeFileSaverTemplate<HtmlCodeResult> {

    @Override
    protected CodeGenTypeEnum getCodeType() {
        return CodeGenTypeEnum.HTML;
    }

    /**
     * 将完整单文件代码写入 index.html。
     *
     * @param result 单文件结构化代码结果
     * @param baseDirPath 已创建的独立输出目录
     */
    @Override
    protected void saveFiles(HtmlCodeResult result, String baseDirPath) {
        writeToFile(baseDirPath, "index.html", result.getHtmlCode());
    }

    /**
     * 单文件模式必须包含有效 HTML，否则保存出的目录无法作为网站运行。
     *
     * @param result 单文件结构化代码结果
     */
    @Override
    protected void validateInput(HtmlCodeResult result) {
        super.validateInput(result);
        if (StrUtil.isBlank(result.getHtmlCode())) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "HTML 代码内容不能为空");
        }
    }
}
