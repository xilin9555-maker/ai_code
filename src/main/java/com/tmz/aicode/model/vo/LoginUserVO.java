package com.tmz.aicode.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 返回给已登录用户的安全视图。
 *
 * 这个对象只保留页面展示和身份判断需要的信息，不包含密码、逻辑删除状态等内部数据。
 */
@Data
public class LoginUserVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户唯一标识。
     */
    private Long id;

    /**
     * 用户登录账号。
     */
    private String userAccount;

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
     * 用户角色，用于前端展示和判断可用功能。
     */
    private String userRole;

    /**
     * 账号创建时间。
     */
    private LocalDateTime createTime;

    /**
     * 用户记录最后更新时间。
     */
    private LocalDateTime updateTime;
}
