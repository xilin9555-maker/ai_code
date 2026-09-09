package com.tmz.aicode.model.dto.app;

import com.tmz.aicode.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;

/**
 * 分页查询应用时接收的筛选条件。
 *
 * 普通用户接口只会采用与该场景有关的字段；管理员接口可以使用这里列出的全部字段。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AppQueryRequest extends PageRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 应用 id，适合精确查询。
     */
    private Long id;

    /**
     * 应用名称，查询时支持模糊匹配。
     */
    private String appName;

    /**
     * 应用封面地址，查询时支持模糊匹配。
     */
    private String cover;

    /**
     * 初始化需求，查询时支持模糊匹配。
     */
    private String initPrompt;

    /**
     * 代码生成类型，使用枚举值精确匹配。
     */
    private String codeGenType;

    /**
     * 部署标识，使用完整值精确匹配。
     */
    private String deployKey;

    /**
     * 展示优先级。
     */
    private Integer priority;

    /**
     * 创建用户 id。
     */
    private Long userId;
}
