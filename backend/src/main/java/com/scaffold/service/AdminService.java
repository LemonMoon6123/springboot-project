package com.scaffold.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scaffold.common.BusinessException;
import com.scaffold.common.PageResult;
import com.scaffold.entity.Admin;
import com.scaffold.entity.Merchant;
import com.scaffold.entity.User;
import com.scaffold.mapper.AdminMapper;
import com.scaffold.mapper.MerchantMapper;
import com.scaffold.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

@Service
public class AdminService {

    @Autowired
    private AdminMapper adminMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private MerchantMapper merchantMapper;

    public PageResult<Admin> page(String keyword, int page, int size) {
        LambdaQueryWrapper<Admin> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Admin::getUsername, keyword)
                    .or().like(Admin::getNickname, keyword)
                    .or().like(Admin::getPhone, keyword));
        }
        wrapper.orderByDesc(Admin::getId);
        Page<Admin> result = adminMapper.selectPage(new Page<>(page, size), wrapper);
        result.getRecords().forEach(a -> a.setPassword(null));
        return PageResult.of(result);
    }

    public Admin getById(Long id) {
        Admin admin = adminMapper.selectById(id);
        if (admin == null) {
            throw new BusinessException(404, "管理员不存在");
        }
        admin.setPassword(null);
        return admin;
    }

    public Admin create(Admin admin) {
        if (!StringUtils.hasText(admin.getUsername())) {
            throw new BusinessException("用户名不能为空");
        }
        if (!StringUtils.hasText(admin.getPassword())) {
            throw new BusinessException("密码不能为空");
        }
        if (findByUsername(admin.getUsername()) != null
                || findUserByUsername(admin.getUsername()) != null
                || findMerchantByUsername(admin.getUsername()) != null) {
            throw new BusinessException("用户名已存在");
        }
        if (admin.getStatus() == null) {
            admin.setStatus(1);
        }
        adminMapper.insert(admin);
        return getById(admin.getId());
    }

    public Admin update(Long id, Admin admin) {
        getById(id);
        admin.setId(id);
        admin.setUsername(null);
        admin.setPassword(null);
        adminMapper.updateById(admin);
        return getById(id);
    }

    public void delete(Long id) {
        getById(id);
        if (adminMapper.selectCount(null) <= 1) {
            throw new BusinessException("至少保留一名管理员");
        }
        adminMapper.deleteById(id);
    }

    public Admin findByUsername(String username) {
        return adminMapper.selectOne(new LambdaQueryWrapper<Admin>()
                .eq(Admin::getUsername, username)
                .last("LIMIT 1"));
    }

    private User findUserByUsername(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)
                .last("LIMIT 1"));
    }

    private Merchant findMerchantByUsername(String username) {
        return merchantMapper.selectOne(new LambdaQueryWrapper<Merchant>()
                .eq(Merchant::getUsername, username)
                .last("LIMIT 1"));
    }
}
