package com.livehouse.config;

import com.livehouse.exception.CustomException;
import com.livehouse.exception.StatusCodeException;
import com.livehouse.entity.Result;
import com.livehouse.entity.StatusCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class WebExceptionAdvice {

    @ExceptionHandler(RuntimeException.class)
    public Result handleRuntimeException(RuntimeException e) {
        log.error(e.toString(), e);
        return Result.fail(StatusCode.SERVER_ERROR);
    }

    @ExceptionHandler(StatusCodeException.class)
    public Result handleStatusCodeException(StatusCodeException e) {
        log.info("业务异常 code={} msg={}", e.getCode(), e.getMessage());
        return Result.fail(e.getStatusCode(), e.getMessage(), e.getData());
    }

    @ExceptionHandler(CustomException.class)
    public Result handleCustomException(CustomException e) {
        log.info(e.toString(), e);
        return Result.fail(StatusCode.SERVER_ERROR, e.getMessage());
    }

}