package com.tmz.aicode.model.dto.app;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 普通用户修改自己应用时接收的参数。
 *
 * 当前只开放应用名称，创建人、生成方式和精选优先级等字段不能通过该请求修改。
 */
@Data
public class AppUpdateRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 要修改的应用 id。
     */
    private Long id;

    /**
     * 新的应用名称。
     */
    private String appName;
}
