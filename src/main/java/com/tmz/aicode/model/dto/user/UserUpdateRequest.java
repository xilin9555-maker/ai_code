package com.tmz.aicode.model.dto.user;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 管理员更新用户信息时提交的请求参数。
 *
 * 用户 id 用于定位记录，其余非空字段用于更新对应的用户资料。
 */
@Data
public class UserUpdateRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 需要更新的用户 id。
     */
    private Long id;

    /**
     * 用户昵称。
     */
    private String userName;

    /**
     * 用户头像地址。
     */
    private String userAvatar;

    /**
     * 用户个人简介。
     */
    private String userProfile;

    /**
     * 用户角色，可选值为 {@code user} 或 {@code admin}。
     */
    private String userRole;
}
