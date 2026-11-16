package com.livehouse.exception;

import com.livehouse.entity.StatusCode;

public class StatusCodeException extends CustomException {

    private final StatusCode statusCode;

    private final Object data;

    public StatusCodeException(StatusCode statusCode) {
        this(statusCode, null, null);
    }

    public StatusCodeException(StatusCode statusCode, String message) {
        this(statusCode, message, null);
    }


    public StatusCodeException(StatusCode statusCode, String message, Object data) {
        super(message != null && !message.isEmpty() ? message : statusCode.getMsg());
        this.statusCode = statusCode;
        this.data = data;
    }

    public StatusCode getStatusCode() {
        return statusCode;
    }

    public Integer getCode() {
        return statusCode.getCode();
    }

    public Object getData() {
        return data;
    }
}
