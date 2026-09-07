package com.tmz.aicode.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 根据 ID 删除数据的通用请求。
 */
@Data
public class DeleteRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
}
