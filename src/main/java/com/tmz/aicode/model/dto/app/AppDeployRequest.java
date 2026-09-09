package com.tmz.aicode.model.dto.app;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 部署应用时接收的参数。
 *
 * 客户端只提交应用 id。部署目录、部署标识和操作人都由服务端确定，避免客户端指定
 * 任意文件路径或冒用其他用户的身份。
 */
@Data
public class AppDeployRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 需要部署的应用 id。
     */
    private Long appId;
}
