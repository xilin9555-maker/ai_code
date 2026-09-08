package com.tmz.aicode.model.dto.user;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 当前登录用户修改个人资料时提交的请求参数。
 *
 * 用户 id 从服务端 Session 中读取，因此客户端不需要传入，也不能借此修改其他人的
 * 资料。角色、账号和密码属于敏感字段，不放进这个请求对象。
 */
@Data
public class UserUpdateMyRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 页面中展示的用户昵称。
     */
    private String userName;

    /**
     * 用户头像的公开访问地址，未设置时可以为空字符串。
     */
    private String userAvatar;

    /**
     * 用户填写的个人简介，未设置时可以为空字符串。
     */
    private String userProfile;
}
