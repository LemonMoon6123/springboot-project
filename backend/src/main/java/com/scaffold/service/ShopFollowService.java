package com.scaffold.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scaffold.common.BusinessException;
import com.scaffold.dto.ShopVO;
import com.scaffold.entity.ShopFollow;
import com.scaffold.mapper.ShopFollowMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class ShopFollowService {

    @Autowired
    private ShopFollowMapper shopFollowMapper;
    @Autowired
    private ShopService shopService;

    public List<ShopFollow> listByUser(Long userId) {
        List<ShopFollow> list = shopFollowMapper.selectList(new LambdaQueryWrapper<ShopFollow>()
                .eq(ShopFollow::getUserId, userId)
                .orderByDesc(ShopFollow::getId));
        for (ShopFollow follow : list) {
            try {
                follow.setShop(shopService.getPublicShop(follow.getMerchantId()));
            } catch (Exception ignored) {
                ShopVO vo = new ShopVO();
                vo.setId(follow.getMerchantId());
                vo.setShopName("店铺已关闭");
                follow.setShop(vo);
            }
        }
        return list;
    }

    public ShopFollow follow(Long userId, Long merchantId) {
        if (merchantId == null) {
            throw new BusinessException("店铺不能为空");
        }
        ShopVO shop = shopService.getPublicShop(merchantId);
        ShopFollow existing = shopFollowMapper.selectOne(new LambdaQueryWrapper<ShopFollow>()
                .eq(ShopFollow::getUserId, userId)
                .eq(ShopFollow::getMerchantId, merchantId)
                .last("LIMIT 1"));
        if (existing != null) {
            existing.setShop(shop);
            return existing;
        }
        ShopFollow row = new ShopFollow();
        row.setUserId(userId);
        row.setMerchantId(merchantId);
        shopFollowMapper.insert(row);
        row.setShop(shop);
        return row;
    }

    public void unfollow(Long userId, Long merchantId) {
        shopFollowMapper.delete(new LambdaQueryWrapper<ShopFollow>()
                .eq(ShopFollow::getUserId, userId)
                .eq(ShopFollow::getMerchantId, merchantId));
    }

    public boolean isFollowed(Long userId, Long merchantId) {
        if (userId == null || merchantId == null) {
            return false;
        }
        Long count = shopFollowMapper.selectCount(new LambdaQueryWrapper<ShopFollow>()
                .eq(ShopFollow::getUserId, userId)
                .eq(ShopFollow::getMerchantId, merchantId));
        return count != null && count > 0;
    }

    public long count(Long userId) {
        Long count = shopFollowMapper.selectCount(new LambdaQueryWrapper<ShopFollow>()
                .eq(ShopFollow::getUserId, userId));
        return count == null ? 0 : count;
    }

    public List<ShopVO> recentShops(Long userId, int limit) {
        List<ShopFollow> list = listByUser(userId);
        List<ShopVO> shops = new ArrayList<>();
        for (ShopFollow f : list) {
            if (f.getShop() != null) {
                shops.add(f.getShop());
            }
            if (shops.size() >= limit) {
                break;
            }
        }
        return shops.stream().filter(Objects::nonNull).toList();
    }
}
