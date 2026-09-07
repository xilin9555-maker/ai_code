package com.tmz.aicode.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

/**
 * 系统支持的用户角色。
 *
 * 角色名称用于页面展示，角色值用于数据库存储和权限判断。把两者放在同一个枚举里，
 * 可以让角色的含义始终保持一致，也能避免在业务代码中到处散落容易写错的字符串。
 */
@Getter
public enum UserRoleEnum {

    /**
     * 普通用户，可以使用面向用户开放的创作和管理能力。
     */
    USER("用户", "user"),

    /**
     * 管理员，可以进入管理功能并处理需要更高权限的操作。
     */
    ADMIN("管理员", "admin");

    /**
     * 适合直接展示给用户看的角色名称。
     */
    private final String text;

    /**
     * 保存在数据库中、参与权限判断的稳定角色值。
     */
    private final String value;

    UserRoleEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据数据库或请求中的角色值找到对应枚举。
     *
     * 空值或未知值都返回 {@code null}，调用方可以据此区分“找到了有效角色”和
     * “当前值无法识别”，再结合具体业务决定使用默认角色还是提示参数错误。
     *
     * @param value 数据库存储的角色值，例如 {@code user} 或 {@code admin}
     * @return 匹配的角色；输入为空或没有匹配项时返回 {@code null}
     */
    public static UserRoleEnum getEnumByValue(String value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        for (UserRoleEnum userRole : UserRoleEnum.values()) {
            if (userRole.value.equals(value)) {
                return userRole;
            }
        }
        return null;
    }
}
