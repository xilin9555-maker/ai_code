package com.tmz.aicode.model.dto.app;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 创建应用时接收的参数。
 *
 * 用户只需要说明想生成什么网站，应用名称、创建人、生成类型和优先级由服务端补充，
 * 防止客户端伪造这些受业务规则控制的字段。
 */
@Data
public class AppAddRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 应用初始化需求，也是首次生成网站代码时使用的描述。
     */
    private String initPrompt;
}
