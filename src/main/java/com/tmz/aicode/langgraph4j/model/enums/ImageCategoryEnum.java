package com.tmz.aicode.langgraph4j.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

/**
 * 工作流中图片资源的业务分类。
 *
 * 分类值会参与图片收集、提示词增强和代码生成节点之间的数据传递。使用枚举可以让各节点
 * 共享一套稳定约定，避免用任意字符串表示图片用途时产生拼写差异。
 */
@Getter
public enum ImageCategoryEnum {

    /** 页面正文、作品或商品等实际内容使用的图片。 */
    CONTENT("内容图片", "CONTENT"),

    /** 品牌标识或站点标志图片。 */
    LOGO("LOGO图片", "LOGO"),

    /** 用于装饰页面和辅助表达主题的插画。 */
    ILLUSTRATION("插画图片", "ILLUSTRATION"),

    /** 架构图、流程图等用于说明结构关系的图片。 */
    ARCHITECTURE("架构图片", "ARCHITECTURE");

    /** 面向用户显示的分类名称。 */
    private final String text;

    /** 用于状态传递和模型结构化输出的稳定值。 */
    private final String value;

    ImageCategoryEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据稳定值查找图片分类。
     *
     * @param value 图片分类值，例如 {@code CONTENT}
     * @return 匹配的分类；输入为空或无法识别时返回 {@code null}
     */
    public static ImageCategoryEnum getEnumByValue(String value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        for (ImageCategoryEnum imageCategory : ImageCategoryEnum.values()) {
            if (imageCategory.value.equals(value)) {
                return imageCategory;
            }
        }
        return null;
    }
}
