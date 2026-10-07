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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class UserService {

    @Autowired
    private UserMapper userMapper;
    @Autowired
    private AdminMapper adminMapper;
    @Autowired
    private MerchantMapper merchantMapper;

    public PageResult<User> page(String keyword, String role, int page, int size) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(User::getUsername, keyword)
                    .or().like(User::getNickname, keyword)
                    .or().like(User::getPhone, keyword));
        }
        wrapper.orderByDesc(User::getId);
        Page<User> result = userMapper.selectPage(new Page<>(page, size), wrapper);
        result.getRecords().forEach(u -> u.setPassword(null));
        return PageResult.of(result);
    }

    public User getById(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        user.setPassword(null);
        return user;
    }

    public User create(User user) {
        if (!StringUtils.hasText(user.getUsername())) {
            throw new BusinessException("用户名不能为空");
        }
        if (!StringUtils.hasText(user.getPassword())) {
            throw new BusinessException("密码不能为空");
        }
        if (findByUsername(user.getUsername()) != null
                || findAdminByUsername(user.getUsername()) != null
                || findMerchantByUsername(user.getUsername()) != null) {
            throw new BusinessException("用户名已存在");
        }
        if (user.getStatus() == null) {
            user.setStatus(1);
        }
        if (user.getBalance() == null) {
            user.setBalance(0.0);
        }
        userMapper.insert(user);
        return getById(user.getId());
    }

    public User update(Long id, User user) {
        getById(id);
        user.setId(id);
        user.setUsername(null);
        user.setPassword(null);
        userMapper.updateById(user);
        return getById(id);
    }

    public void delete(Long id) {
        getById(id);
        userMapper.deleteById(id);
    }

    public User findByUsername(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)
                .last("LIMIT 1"));
    }

    private Admin findAdminByUsername(String username) {
        return adminMapper.selectOne(new LambdaQueryWrapper<Admin>()
                .eq(Admin::getUsername, username)
                .last("LIMIT 1"));
    }

    private Merchant findMerchantByUsername(String username) {
        return merchantMapper.selectOne(new LambdaQueryWrapper<Merchant>()
                .eq(Merchant::getUsername, username)
                .last("LIMIT 1"));
    }
}
