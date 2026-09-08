package com.tmz.aicode.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

/**
 * 网页代码的生成方式。
 *
 * text 用于界面展示，value 适合保存在数据库或作为接口参数。业务层使用枚举分支处理，
 * 可以避免在不同位置重复比较容易写错的字符串。
 */
@Getter
public enum CodeGenTypeEnum {

    /**
     * 把 HTML、CSS 和 JavaScript 放入同一个 index.html 文件。
     */
    HTML("原生 HTML 模式", "html"),

    /**
     * 分别生成 index.html、style.css 和 script.js 三个文件。
     */
    MULTI_FILE("原生多文件模式", "multi_file");

    /**
     * 适合直接显示给用户的模式名称。
     */
    private final String text;

    /**
     * 用于数据存储和程序判断的稳定值。
     */
    private final String value;

    CodeGenTypeEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据稳定值查找对应的生成方式。
     *
     * @param value 接口或数据库中的生成方式，例如 {@code html} 或 {@code multi_file}
     * @return 匹配的枚举；输入为空或无法识别时返回 {@code null}
     */
    public static CodeGenTypeEnum getEnumByValue(String value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        for (CodeGenTypeEnum codeGenType : CodeGenTypeEnum.values()) {
            if (codeGenType.value.equals(value)) {
                return codeGenType;
            }
        }
        return null;
    }
}
