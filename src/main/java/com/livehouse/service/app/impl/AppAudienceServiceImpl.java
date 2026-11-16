package com.livehouse.service.app.impl;

import cn.hutool.core.util.StrUtil;
import com.livehouse.dto.app.AudienceCreateRequest;
import com.livehouse.entity.Audience;
import com.livehouse.exception.StatusCodeException;
import com.livehouse.entity.StatusCode;
import com.livehouse.mapper.AudienceMapper;
import com.livehouse.service.app.AppAudienceService;
import com.livehouse.utils.IdCardCryptoUtils;
import com.livehouse.utils.RegexUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AppAudienceServiceImpl implements AppAudienceService {

    private static final int MAX_AUDIENCE = 5;
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private AudienceMapper audienceMapper;
    @Resource
    private IdCardCryptoUtils idCardCryptoUtils;

    @Override
    public Map<String, Object> list(Long userId) {
        List<Audience> list = audienceMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Audience>()
                        .eq(Audience::getUserId, userId)
                        .orderByDesc(Audience::getIsDefault)
                        .orderByDesc(Audience::getLastUsedTime));
        List<Map<String, Object>> records = list.stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", String.valueOf(a.getId()));
            m.put("name", a.getName());
            m.put("idCardMask", a.getIdCardMask());
            m.put("isDefault", Objects.equals(a.getIsDefault(), 1));
            m.put("authStatus", a.getAuthStatus());
            m.put("lastUsedTime", a.getLastUsedTime() == null ? null : a.getLastUsedTime().format(DT));
            m.put("canDelete", true);
            m.put("deleteBlockReason", null);
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("maxCount", MAX_AUDIENCE);
        data.put("records", records);
        return data;
    }

    @Override
    public Map<String, Object> create(Long userId, AudienceCreateRequest request) {
        if (request == null || StrUtil.isBlank(request.getName()) || StrUtil.isBlank(request.getIdCard())) {
            throw new StatusCodeException(StatusCode.PARAM_ERROR);
        }
        if (request.getIdType() == null || request.getIdType() == 1) {
            if (!RegexUtils.isIdCardValid(request.getIdCard())) {
                throw new StatusCodeException(StatusCode.ID_CARD_INVALID);
            }
        }

        long count = audienceMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Audience>()
                        .eq(Audience::getUserId, userId));
        if (count >= MAX_AUDIENCE) {
            throw new StatusCodeException(StatusCode.AUDIENCE_LIMIT_EXCEEDED);
        }

        String hash = idCardCryptoUtils.hash(request.getIdCard());
        long dup = audienceMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Audience>()
                        .eq(Audience::getUserId, userId)
                        .eq(Audience::getIdCardHash, hash));
        if (dup > 0) {
            throw new StatusCodeException(StatusCode.AUDIENCE_DUPLICATED);
        }

        Audience audience = new Audience();
        audience.setUserId(userId);
        audience.setName(request.getName().trim());
        audience.setIdType(request.getIdType() == null ? 1 : request.getIdType());
        audience.setIdCardEnc(idCardCryptoUtils.encrypt(request.getIdCard()));
        audience.setIdCardMask(idCardCryptoUtils.mask(request.getIdCard()));
        audience.setIdCardHash(hash);
        audience.setPhone(request.getPhone());
        audience.setIsDefault(count == 0 ? 1 : 0);
        audience.setAuthStatus(1);
        audience.setCreateTime(LocalDateTime.now());
        audience.setUpdateTime(LocalDateTime.now());
        audienceMapper.insert(audience);

        return list(userId);
    }
}