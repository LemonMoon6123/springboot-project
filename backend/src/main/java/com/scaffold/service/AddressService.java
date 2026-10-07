package com.scaffold.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.scaffold.common.BusinessException;
import com.scaffold.entity.UserAddress;
import com.scaffold.mapper.UserAddressMapper;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class AddressService {

    @Autowired
    private UserAddressMapper userAddressMapper;

    public List<UserAddress> list(Long userId) {
        return userAddressMapper.selectList(new LambdaQueryWrapper<UserAddress>()
                .eq(UserAddress::getUserId, userId)
                .orderByDesc(UserAddress::getIsDefault)
                .orderByDesc(UserAddress::getId));
    }

    public UserAddress getById(Long userId, Long id) {
        UserAddress address = userAddressMapper.selectById(id);
        if (address == null || !address.getUserId().equals(userId)) {
            throw new BusinessException(404, "地址不存在");
        }
        return address;
    }

    public UserAddress create(Long userId, UserAddress address) {
        validate(address);
        address.setUserId(userId);
        if (address.getIsDefault() != null && address.getIsDefault() == 1) {
            clearDefault(userId);
        } else if (address.getIsDefault() == null) {
            address.setIsDefault(0);
        }
        userAddressMapper.insert(address);
        return userAddressMapper.selectById(address.getId());
    }

    public UserAddress update(Long userId, Long id, UserAddress address) {
        getById(userId, id);
        validate(address);
        address.setId(id);
        address.setUserId(userId);
        if (address.getIsDefault() != null && address.getIsDefault() == 1) {
            clearDefault(userId);
        }
        userAddressMapper.updateById(address);
        return userAddressMapper.selectById(id);
    }

    public void delete(Long userId, Long id) {
        getById(userId, id);
        userAddressMapper.deleteById(id);
    }

    public UserAddress setDefault(Long userId, Long id) {
        getById(userId, id);
        clearDefault(userId);
        UserAddress patch = new UserAddress();
        patch.setId(id);
        patch.setIsDefault(1);
        userAddressMapper.updateById(patch);
        return userAddressMapper.selectById(id);
    }

    private void clearDefault(Long userId) {
        userAddressMapper.update(null, new LambdaUpdateWrapper<UserAddress>()
                .eq(UserAddress::getUserId, userId)
                .set(UserAddress::getIsDefault, 0));
    }

    private void validate(UserAddress address) {
        if (!StringUtils.hasText(address.getReceiver())) {
            throw new BusinessException("收货人不能为空");
        }
        if (!StringUtils.hasText(address.getPhone())) {
            throw new BusinessException("手机号不能为空");
        }
        if (!StringUtils.hasText(address.getDetail())) {
            throw new BusinessException("详细地址不能为空");
        }
    }
}
