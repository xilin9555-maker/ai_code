package com.tmz.aicode.exception;

import com.tmz.aicode.common.BaseResponse;
import com.tmz.aicode.common.ResultUtils;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 将控制器异常转换为统一响应，详细错误仅写入日志。
 */
@Hidden
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 请求体不是合法 JSON，或字段类型无法转换时返回明确的参数错误。
     *
     * 这类异常发生在进入 Controller 方法之前，单独处理可以避免客户端只看到笼统的
     * 系统错误，也便于快速发现命令行引号或请求体格式问题。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public BaseResponse<?> httpMessageNotReadableExceptionHandler(HttpMessageNotReadableException e) {
        log.warn("请求体格式错误", e);
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, "请求体格式错误");
    }

    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> businessExceptionHandler(BusinessException e) {
        log.error("BusinessException", e);
        return ResultUtils.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public BaseResponse<?> runtimeExceptionHandler(RuntimeException e) {
        log.error("RuntimeException", e);
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误");
    }
}
