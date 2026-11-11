package com.livehouse.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result {
    private Boolean success;
    private String errorMsg;
    private Object data;
    private Long total;
    private Integer code;

    public static Result ok(){
        return new Result(true, null, null, null,StatusCode.SUCCESS.getCode());
    }
    
    public static Result ok(Object data){
        return new Result(true, null, data, null,StatusCode.SUCCESS.getCode());
    }
    
    public static Result ok(List<?> data, Long total){
        return new Result(true, null, data, total,StatusCode.SUCCESS.getCode());
    }

    public static Result fail(StatusCode sc) {
        return new Result(false, sc.getMsg(), null, null, sc.getCode());
    }

    public static Result fail(StatusCode sc, String errorMsg) {
        return new Result(false, errorMsg, null, null, sc.getCode());
    }

    /**
     * 业务失败 + 附带数据。
     * data 用于前端需要接着处理错误的场景，例如重复核销时把首次核销时间和闸机口带回去。
     */
    public static Result fail(StatusCode sc, String errorMsg, Object data) {
        return new Result(false, errorMsg != null && !errorMsg.isEmpty() ? errorMsg : sc.getMsg(), data, null, sc.getCode());
    }
}