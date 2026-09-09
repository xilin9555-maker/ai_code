package com.tmz.aicode.model.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 返回给前端的应用信息。
 *
 * 视图对象在应用字段之外补充了创建者的脱敏信息，前端拿到一次响应后就能同时展示
 * 应用卡片和作者资料，不需要再根据 userId 逐个请求用户接口。
 */
@Data
public class AppVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 应用 id。
     */
    private Long id;

    /**
     * 应用名称。
     */
    private String appName;

    /**
     * 应用封面地址。
     */
    private String cover;

    /**
     * 创建应用时填写的初始化需求。
     */
    private String initPrompt;

    /**
     * 代码生成类型。
     */
    private String codeGenType;

    /**
     * 部署标识。
     */
    private String deployKey;

    /**
     * 最近一次部署时间。
     */
    private LocalDateTime deployedTime;

    /**
     * 展示优先级。
     */
    private Integer priority;

    /**
     * 创建用户 id。
     */
    private Long userId;

    /**
     * 应用创建时间。
     */
    private LocalDateTime createTime;

    /**
     * 应用最近一次更新时间。
     */
    private LocalDateTime updateTime;

    /**
     * 创建者的脱敏公开信息。
     */
    private UserVO user;
}
