package com.scaffold.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scaffold.common.BusinessException;
import com.scaffold.common.PageResult;
import com.scaffold.dto.ShopVO;
import com.scaffold.entity.Merchant;
import com.scaffold.entity.Product;
import com.scaffold.mapper.MerchantMapper;
import com.scaffold.mapper.ProductMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ShopService {

    @Autowired
    private MerchantMapper merchantMapper;
    @Autowired
    private ProductMapper productMapper;

    public PageResult<ShopVO> page(String keyword, int page, int size) {
        LambdaQueryWrapper<Merchant> wrapper = new LambdaQueryWrapper<Merchant>()
                .eq(Merchant::getAuditStatus, 1)
                .eq(Merchant::getStatus, 1);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Merchant::getShopName, keyword)
                    .or().like(Merchant::getNickname, keyword)
                    .or().like(Merchant::getShopDesc, keyword));
        }
        wrapper.orderByDesc(Merchant::getId);
        PageResult<Merchant> raw = PageResult.of(merchantMapper.selectPage(new Page<>(page, size), wrapper));
        return PageResult.of(raw.getTotal(), toShopList(raw.getList()));
    }

    public ShopVO getPublicShop(Long id) {
        Merchant merchant = merchantMapper.selectById(id);
        if (merchant == null
                || merchant.getAuditStatus() == null
                || merchant.getAuditStatus() != 1
                || merchant.getStatus() == null
                || merchant.getStatus() != 1) {
            throw new BusinessException(404, "店铺不存在或未开放");
        }
        Map<Long, int[]> stats = loadStats(Collections.singleton(id));
        return toShop(merchant, stats.getOrDefault(id, new int[]{0, 0}));
    }

    private List<ShopVO> toShopList(List<Merchant> merchants) {
        if (merchants == null || merchants.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> ids = merchants.stream().map(Merchant::getId).collect(Collectors.toSet());
        Map<Long, int[]> stats = loadStats(ids);
        return merchants.stream()
                .map(m -> toShop(m, stats.getOrDefault(m.getId(), new int[]{0, 0})))
                .collect(Collectors.toList());
    }

    private Map<Long, int[]> loadStats(Set<Long> merchantIds) {
        Map<Long, int[]> map = new HashMap<>();
        if (merchantIds == null || merchantIds.isEmpty()) {
            return map;
        }
        List<Product> products = productMapper.selectList(new LambdaQueryWrapper<Product>()
                .in(Product::getMerchantId, merchantIds)
                .eq(Product::getStatus, 1)
                .select(Product::getMerchantId, Product::getSales));
        for (Product p : products) {
            int[] stat = map.computeIfAbsent(p.getMerchantId(), k -> new int[]{0, 0});
            stat[0] += 1;
            stat[1] += p.getSales() == null ? 0 : p.getSales();
        }
        return map;
    }

    private ShopVO toShop(Merchant merchant, int[] stats) {
        ShopVO vo = new ShopVO();
        vo.setId(merchant.getId());
        vo.setShopName(StringUtils.hasText(merchant.getShopName()) ? merchant.getShopName()
                : (StringUtils.hasText(merchant.getNickname()) ? merchant.getNickname() : merchant.getUsername()));
        vo.setShopDesc(StringUtils.hasText(merchant.getShopDesc()) ? merchant.getShopDesc() : "品质好物，值得信赖");
        vo.setAvatar(merchant.getAvatar());
        vo.setNickname(merchant.getNickname());
        vo.setProductCount(stats[0]);
        vo.setSalesCount(stats[1]);
        vo.setCreatedAt(merchant.getCreatedAt());
        return vo;
    }
}
