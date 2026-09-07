package com.tmz.aicode.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 对外展示的用户信息。
 *
 * 该视图保留用户管理页面需要的公开字段，不包含密码和逻辑删除状态等内部数据。
 */
@Data
public class UserVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户 id。
     */
    private Long id;

    /**
     * 用户账号。
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
     * 用户角色。
     */
    private String userRole;

    /**
     * 账号创建时间。
     */
    private LocalDateTime createTime;
}
