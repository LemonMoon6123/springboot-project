package com.scaffold.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scaffold.common.BusinessException;
import com.scaffold.common.PageResult;
import com.scaffold.entity.Announcement;
import com.scaffold.mapper.AnnouncementMapper;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class AnnouncementService {

    @Autowired
    private AnnouncementMapper announcementMapper;

    public PageResult<Announcement> page(String keyword, Integer status, int page, int size) {
        LambdaQueryWrapper<Announcement> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Announcement::getTitle, keyword)
                    .or().like(Announcement::getContent, keyword));
        }
        if (status != null) {
            wrapper.eq(Announcement::getStatus, status);
        }
        wrapper.orderByDesc(Announcement::getId);
        return PageResult.of(announcementMapper.selectPage(new Page<>(page, size), wrapper));
    }

    public List<Announcement> listEnabled() {
        return announcementMapper.selectList(new LambdaQueryWrapper<Announcement>()
                .eq(Announcement::getStatus, 1)
                .orderByDesc(Announcement::getId));
    }

    public Announcement getById(Long id) {
        Announcement announcement = announcementMapper.selectById(id);
        if (announcement == null) {
            throw new BusinessException(404, "公告不存在");
        }
        return announcement;
    }

    public Announcement create(Announcement announcement) {
        if (!StringUtils.hasText(announcement.getTitle())) {
            throw new BusinessException("标题不能为空");
        }
        if (announcement.getStatus() == null) {
            announcement.setStatus(1);
        }
        announcementMapper.insert(announcement);
        return announcementMapper.selectById(announcement.getId());
    }

    public Announcement update(Long id, Announcement announcement) {
        getById(id);
        announcement.setId(id);
        announcementMapper.updateById(announcement);
        return announcementMapper.selectById(id);
    }

    public void delete(Long id) {
        getById(id);
        announcementMapper.deleteById(id);
    }
}
