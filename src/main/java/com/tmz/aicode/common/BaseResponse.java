package com.tmz.aicode.common;

import com.tmz.aicode.exception.ErrorCode;
import lombok.Data;

import java.io.Serializable;

/**
 * 接口统一响应，包含业务状态码、数据和提示信息。
 */
@Data
public class BaseResponse<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private int code;

    private T data;

    private String message;

    public BaseResponse(int code, T data, String message) {
        this.code = code;
        this.data = data;
        this.message = message;
    }

    public BaseResponse(int code, T data) {
        this(code, data, "");
    }

    public BaseResponse(ErrorCode errorCode) {
        this(errorCode.getCode(), null, errorCode.getMessage());
    }
}
