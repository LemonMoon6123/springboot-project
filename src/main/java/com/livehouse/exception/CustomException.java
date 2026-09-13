package com.livehouse.exception;

/**
 * @Auther: shaolei
 * @Date: 2026/9/13-14:00
 * @Description：自定义异常类
 */
public class CustomException extends RuntimeException {
    public CustomException(String message) {
        super(message);
    }
}
