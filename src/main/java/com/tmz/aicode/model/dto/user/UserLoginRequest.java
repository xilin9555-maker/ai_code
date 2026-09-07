package com.tmz.aicode.model.dto.user;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户提交登录信息时使用的请求参数。
 *
 * 登录接口只需要账号和密码。使用单独的请求类可以明确接口允许接收的内容，
 * 避免把角色、用户资料等与身份验证无关的字段带入登录流程。
 */
@Data
public class UserLoginRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 3191241716373120793L;

    /**
     * 用户注册时设置的登录账号。
     *
     * 服务端会使用这个账号查找对应用户，并继续校验密码是否正确。
     */
    private String userAccount;

    /**
     * 用户本次输入的登录密码。
     *
     * 这个字段只在身份验证过程中短暂使用，后续会经过与注册时相同的处理，
     * 再与数据库中保存的密码摘要进行比较。
     */
    private String userPassword;
}
