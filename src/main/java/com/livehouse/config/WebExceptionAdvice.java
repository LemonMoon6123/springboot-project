package com.livehouse.config;

import com.livehouse.exception.CustomException;
import com.livehouse.dto.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class WebExceptionAdvice {

    @ExceptionHandler(RuntimeException.class)
    public Result handleRuntimeException(RuntimeException e) {
        log.error(e.toString(), e);
        return Result.fail("服务器异常");
    }

    @ExceptionHandler(CustomException.class)
    public Result handleStockException(CustomException e){
        log.info(e.toString(),e);
        return Result.fail(e.getMessage());
    }


}