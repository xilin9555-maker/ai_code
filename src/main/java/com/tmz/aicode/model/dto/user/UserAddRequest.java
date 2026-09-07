package com.tmz.aicode.model.dto.user;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 管理员创建用户时提交的请求参数。
 *
 * 密码不由请求方提供，创建用户时会由服务端设置统一的初始密码。
 */
@Data
public class UserAddRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户昵称。
     */
    private String userName;

    /**
     * 用户登录账号。
     */
    private String userAccount;

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
