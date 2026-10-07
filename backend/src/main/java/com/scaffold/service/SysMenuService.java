package com.scaffold.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scaffold.common.BusinessException;
import com.scaffold.common.PageResult;
import com.scaffold.entity.SysMenu;
import com.scaffold.mapper.SysMenuMapper;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@Service
public class SysMenuService {

    @Autowired
    private SysMenuMapper sysMenuMapper;

    public List<SysMenu> listAll() {
        return sysMenuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                .orderByAsc(SysMenu::getSortOrder)
                .orderByAsc(SysMenu::getId));
    }

    public PageResult<SysMenu> page(int page, int size) {
        Page<SysMenu> result = sysMenuMapper.selectPage(
                new Page<>(page, size),
                new LambdaQueryWrapper<SysMenu>()
                        .orderByAsc(SysMenu::getSortOrder)
                        .orderByDesc(SysMenu::getId)
        );
        return PageResult.of(result);
    }

    public SysMenu getById(Long id) {
        SysMenu menu = sysMenuMapper.selectById(id);
        if (menu == null) {
            throw new BusinessException(404, "菜单不存在");
        }
        return menu;
    }

    public SysMenu create(SysMenu menu) {
        if (menu.getStatus() == null) {
            menu.setStatus(1);
        }
        if (menu.getSortOrder() == null) {
            menu.setSortOrder(0);
        }
        if (menu.getParentId() == null) {
            menu.setParentId(0L);
        }
        sysMenuMapper.insert(menu);
        return sysMenuMapper.selectById(menu.getId());
    }

    public SysMenu update(Long id, SysMenu menu) {
        getById(id);
        menu.setId(id);
        sysMenuMapper.updateById(menu);
        return sysMenuMapper.selectById(id);
    }

    public void delete(Long id) {
        getById(id);
        sysMenuMapper.deleteById(id);
    }
}
