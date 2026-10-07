package com.scaffold.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("sys_config")
public class SysConfig {

    // 这行注解告诉 MyBatis-Plus：configKey 是主键，对应数据库列 config_key，主键值不由框架生成，而是由你手动设置，否则在新增时会报错。
    @TableId(value = "config_key", type = IdType.INPUT)
    private String configKey;
    private String configValue;
    private String remark;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updatedAt;
}
