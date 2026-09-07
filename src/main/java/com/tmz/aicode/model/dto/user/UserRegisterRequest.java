package com.tmz.aicode.model.dto.user;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户提交注册信息时使用的请求参数。
 *
 * 注册接口只接收创建账号真正需要的内容，不直接使用用户实体类，
 * 这样可以避免客户端传入角色、删除状态或创建时间等不应由用户决定的字段。
 */
@Data
public class UserRegisterRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 3191241716373120793L;

    /**
     * 用户希望注册的登录账号。
     *
     * 具体的长度、格式和重复性检查由注册服务统一处理。
     */
    private String userAccount;

    /**
     * 用户第一次输入的登录密码。
     *
     * 服务端校验通过后只保存加密结果，不应把这个明文值写入数据库或日志。
     */
    private String userPassword;

    /**
     * 用户再次输入的确认密码。
     *
     * 它只用于确认两次输入一致，不需要持久化到用户表。
     */
    private String checkPassword;
}
