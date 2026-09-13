package com.hmdp.entity.service.impl;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.entity.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOPTYPE_TTL;
import static com.hmdp.utils.RedisConstants.SHOP_TYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    public Result queryTypeList() {
        // 1.先从Redis数据库中查询店铺的类型信息。
        String key = SHOP_TYPE_KEY;
        // 2.如果查找到，就直接获取并返回数据。
        String typeListJson = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(typeListJson)) {
            List<ShopType> list = JSONUtil.toList(typeListJson, ShopType.class);
            return Result.ok(list);
        }
        // 3.如果查不到，就从数据库中查找。
        List<ShopType> list = query().orderByAsc("sort").list();
        // 4.数据库中如果查不到信息，就返回错误提示信息。
        if(list.isEmpty()){
            return Result.fail("未查找到店铺类型信息！");
        }
        // 5.如果查找到，就先存Redis中作为缓存数据。
        typeListJson = JSONUtil.toJsonStr(list);
        stringRedisTemplate.opsForValue().set(key,typeListJson,CACHE_SHOPTYPE_TTL + RandomUtil.randomLong(0,10), TimeUnit.MINUTES);
        // 6.返回结果。
        return Result.ok(list);
    }
}
