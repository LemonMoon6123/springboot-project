package com.scaffold.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scaffold.common.BusinessException;
import com.scaffold.common.PageResult;
import com.scaffold.entity.Category;
import com.scaffold.mapper.CategoryMapper;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class CategoryService {

    @Autowired
    private CategoryMapper categoryMapper;

    public PageResult<Category> page(String keyword, Integer status, int page, int size) {
        LambdaQueryWrapper<Category> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Category::getName, keyword);
        }
        if (status != null) {
            wrapper.eq(Category::getStatus, status);
        }
        wrapper.orderByAsc(Category::getSortOrder).orderByAsc(Category::getId);
        return PageResult.of(categoryMapper.selectPage(new Page<>(page, size), wrapper));
    }

    public List<Category> listEnabled() {
        return categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .eq(Category::getStatus, 1)
                .orderByAsc(Category::getSortOrder)
                .orderByAsc(Category::getId));
    }

    public Category getById(Long id) {
        Category category = categoryMapper.selectById(id);
        if (category == null) {
            throw new BusinessException(404, "分类不存在");
        }
        return category;
    }

    public Category create(Category category) {
        normalize(category);
        categoryMapper.insert(category);
        return categoryMapper.selectById(category.getId());
    }

    public Category update(Long id, Category category) {
        getById(id);
        category.setId(id);
        normalize(category);
        categoryMapper.updateById(category);
        return categoryMapper.selectById(id);
    }

    public void delete(Long id) {
        getById(id);
        categoryMapper.deleteById(id);
    }

    private void normalize(Category category) {
        if (category.getParentId() == null) {
            category.setParentId(0L);
        }
        if (category.getSortOrder() == null) {
            category.setSortOrder(0);
        }
        if (category.getStatus() == null) {
            category.setStatus(1);
        }
    }
}
