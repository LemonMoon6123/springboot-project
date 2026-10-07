package com.scaffold.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.scaffold.common.DateTimes;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

/** 新增、更新时自动写入字符串时间 */
@Component
public class TimeFillHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        String now = DateTimes.now();
        fillIfEmpty(metaObject, "createdAt", now);
        fillIfEmpty(metaObject, "updatedAt", now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        if (metaObject.hasSetter("updatedAt")) {
            setFieldValByName("updatedAt", DateTimes.now(), metaObject);
        }
    }

    private void fillIfEmpty(MetaObject metaObject, String field, String value) {
        if (metaObject.hasSetter(field) && getFieldValByName(field, metaObject) == null) {
            setFieldValByName(field, value, metaObject);
        }
    }
}
