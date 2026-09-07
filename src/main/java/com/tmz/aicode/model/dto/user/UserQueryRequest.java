package com.tmz.aicode.model.dto.user;

import com.tmz.aicode.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;

/**
 * 分页查询用户时提交的筛选条件。
 *
 * 继承 {@link PageRequest} 后，可以同时接收页码、每页数量和排序参数。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserQueryRequest extends PageRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户 id，适合精确查询。
     */
    private Long id;

    /**
     * 用户昵称，查询时支持模糊匹配。
     */
    private String userName;

    /**
     * 用户账号，查询时支持模糊匹配。
     */
    private String userAccount;

    /**
     * 用户简介，查询时支持模糊匹配。
     */
    private String userProfile;

    /**
     * 用户角色，适合精确查询。
     */
    private String userRole;
}
