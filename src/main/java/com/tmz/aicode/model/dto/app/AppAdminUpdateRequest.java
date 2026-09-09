package com.tmz.aicode.model.dto.app;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 管理员修改应用时接收的参数。
 *
 * 管理员可以维护页面展示信息，并通过优先级决定应用是否进入精选区域。
 */
@Data
public class AppAdminUpdateRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 要修改的应用 id。
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
     * 应用优先级，精选应用使用固定的高优先级。
     */
    private Integer priority;
}
