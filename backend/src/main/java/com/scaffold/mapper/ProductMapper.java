package com.scaffold.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scaffold.entity.Product;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {
}
