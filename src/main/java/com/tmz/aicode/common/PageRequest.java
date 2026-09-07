package com.tmz.aicode.common;

import lombok.Data;

/**
 * 通用分页请求。
 */
@Data
public class PageRequest {

    /**
     * 当前页号，从 1 开始。
     */
    private int pageNum = 1;

    private int pageSize = 10;

    private String sortField;

    /**
     * 默认降序。
     */
    private String sortOrder = "descend";
}
