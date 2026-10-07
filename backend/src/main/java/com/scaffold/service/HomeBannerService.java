package com.scaffold.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scaffold.common.BusinessException;
import com.scaffold.common.PageResult;
import com.scaffold.entity.HomeBanner;
import com.scaffold.mapper.HomeBannerMapper;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class HomeBannerService {

    @Autowired
    private HomeBannerMapper homeBannerMapper;

    public PageResult<HomeBanner> page(String keyword, int page, int size) {
        LambdaQueryWrapper<HomeBanner> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(HomeBanner::getTitle, keyword)
                    .or().like(HomeBanner::getSubtitle, keyword));
        }
        wrapper.orderByAsc(HomeBanner::getSortOrder).orderByAsc(HomeBanner::getId);
        return PageResult.of(homeBannerMapper.selectPage(new Page<>(page, size), wrapper));
    }

    public List<HomeBanner> listEnabled() {
        return homeBannerMapper.selectList(new LambdaQueryWrapper<HomeBanner>()
                .eq(HomeBanner::getStatus, 1)
                .orderByAsc(HomeBanner::getSortOrder)
                .orderByAsc(HomeBanner::getId));
    }

    public HomeBanner getById(Long id) {
        HomeBanner banner = homeBannerMapper.selectById(id);
        if (banner == null) {
            throw new BusinessException(404, "轮播图不存在");
        }
        return banner;
    }

    public HomeBanner create(HomeBanner banner) {
        normalize(banner);
        homeBannerMapper.insert(banner);
        return homeBannerMapper.selectById(banner.getId());
    }

    public HomeBanner update(Long id, HomeBanner banner) {
        getById(id);
        banner.setId(id);
        normalize(banner);
        homeBannerMapper.updateById(banner);
        return homeBannerMapper.selectById(id);
    }

    public void delete(Long id) {
        getById(id);
        homeBannerMapper.deleteById(id);
    }

    private void normalize(HomeBanner banner) {
        if (banner.getSortOrder() == null) {
            banner.setSortOrder(0);
        }
        if (banner.getStatus() == null) {
            banner.setStatus(1);
        }
        if (!StringUtils.hasText(banner.getCtaText())) {
            banner.setCtaText("了解更多");
        }
        if (!StringUtils.hasText(banner.getLinkUrl())) {
            banner.setLinkUrl("/courses");
        }
    }
}
