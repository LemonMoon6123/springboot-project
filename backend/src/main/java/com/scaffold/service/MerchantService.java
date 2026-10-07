package com.scaffold.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scaffold.common.BusinessException;
import com.scaffold.common.PageResult;
import com.scaffold.dto.MerchantAuditRequest;
import com.scaffold.entity.Merchant;
import com.scaffold.mapper.MerchantMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class MerchantService {

    @Autowired
    private MerchantMapper merchantMapper;

    public PageResult<Merchant> page(String keyword, Integer auditStatus, int page, int size) {
        LambdaQueryWrapper<Merchant> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Merchant::getUsername, keyword)
                    .or().like(Merchant::getNickname, keyword)
                    .or().like(Merchant::getShopName, keyword)
                    .or().like(Merchant::getPhone, keyword));
        }
        if (auditStatus != null) {
            wrapper.eq(Merchant::getAuditStatus, auditStatus);
        }
        wrapper.orderByDesc(Merchant::getId);
        Page<Merchant> result = merchantMapper.selectPage(new Page<>(page, size), wrapper);
        result.getRecords().forEach(m -> m.setPassword(null));
        return PageResult.of(result);
    }

    public Merchant getById(Long id) {
        Merchant merchant = merchantMapper.selectById(id);
        if (merchant == null) {
            throw new BusinessException(404, "商家不存在");
        }
        merchant.setPassword(null);
        return merchant;
    }

    public Merchant findByUsername(String username) {
        return merchantMapper.selectOne(new LambdaQueryWrapper<Merchant>()
                .eq(Merchant::getUsername, username)
                .last("LIMIT 1"));
    }

    public Merchant audit(Long id, MerchantAuditRequest request) {
        getById(id);
        if (request.getAuditStatus() == null || (request.getAuditStatus() != 1 && request.getAuditStatus() != 2)) {
            throw new BusinessException("审核状态无效");
        }
        Merchant patch = new Merchant();
        patch.setId(id);
        patch.setAuditStatus(request.getAuditStatus());
        patch.setAuditRemark(request.getAuditRemark());
        merchantMapper.updateById(patch);
        return getById(id);
    }

    public Merchant updateProfile(Long merchantId, Merchant body) {
        getById(merchantId);
        Merchant patch = new Merchant();
        patch.setId(merchantId);
        if (StringUtils.hasText(body.getNickname())) {
            patch.setNickname(body.getNickname());
        }
        if (body.getAvatar() != null) {
            patch.setAvatar(body.getAvatar());
        }
        if (body.getPhone() != null) {
            patch.setPhone(body.getPhone());
        }
        if (StringUtils.hasText(body.getShopName())) {
            patch.setShopName(body.getShopName());
        }
        if (body.getShopDesc() != null) {
            patch.setShopDesc(body.getShopDesc());
        }
        merchantMapper.updateById(patch);
        return getById(merchantId);
    }
}
