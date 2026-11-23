package com.livehouse.service.app.impl;

import cn.hutool.core.util.StrUtil;
import com.livehouse.dto.app.AudienceCreateRequest;
import com.livehouse.dto.app.AudienceUpdateRequest;
import com.livehouse.entity.Audience;
import com.livehouse.entity.OrderAudience;
import com.livehouse.mapper.OrderAudienceMapper;
import com.livehouse.exception.StatusCodeException;
import com.livehouse.entity.StatusCode;
import com.livehouse.entity.RealNameAuth;
import com.livehouse.mapper.AudienceMapper;
import com.livehouse.mapper.RealNameAuthMapper;
import com.livehouse.service.app.AppAudienceService;
import com.livehouse.vo.app.audience.AudienceItemVo;
import com.livehouse.vo.app.audience.AudienceListVo;
import com.livehouse.utils.IdCardCryptoUtils;
import com.livehouse.utils.RegexUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Duration;
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
    @Resource
    private OrderAudienceMapper orderAudienceMapper;
    @Resource
    private RealNameAuthMapper realNameAuthMapper;

    /** 观演人证件已校验通过，与下单页 authStatus===2 对齐 */
    private static final int AUDIENCE_AUTH_PASS = 2;

    @Override
    public AudienceListVo list(Long userId) {
        List<Audience> list = audienceMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Audience>()
                        .eq(Audience::getUserId, userId)
                        .orderByDesc(Audience::getIsDefault)
                        .orderByDesc(Audience::getLastUsedTime));
        List<AudienceItemVo> records = list.stream().map(a -> {
            AudienceItemVo m = new AudienceItemVo();
            m.setId(String.valueOf(a.getId()));
            m.setName(a.getName());
            m.setIdCardMask(a.getIdCardMask());
            m.setIsDefault(Objects.equals(a.getIsDefault(), 1));
            m.setAuthStatus(a.getAuthStatus());
            m.setAuthStatusText(audienceAuthText(a.getAuthStatus()));
            m.setLastUsedTime(a.getLastUsedTime() == null ? null : a.getLastUsedTime().format(DT));
            m.setLastUsed(a.getLastUsedTime() == null ? null : a.getLastUsedTime().format(DT));
            m.setCanDelete(true);
            m.setDeleteBlockReason(null);
            return m;
        }).collect(Collectors.toList());

        AudienceListVo data = new AudienceListVo();
        data.setMaxCount(MAX_AUDIENCE);
        data.setRecords(records);
        return data;
    }

    @Override
    public AudienceListVo create(Long userId, AudienceCreateRequest request) {
        if (request == null || StrUtil.isBlank(request.getName())) {
            throw new StatusCodeException(StatusCode.PARAM_ERROR);
        }
        String idCard = request.getIdCard() == null ? null : request.getIdCard().trim();
        boolean useAccountRealName = Boolean.TRUE.equals(request.getUseAccountRealName());
        if (useAccountRealName) {
            RealNameAuth auth = realNameAuthMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RealNameAuth>()
                            .eq(RealNameAuth::getUserId, userId));
            if (auth == null || auth.getAuthStatus() == null
                    || auth.getAuthStatus() != RealNameAuth.STATUS_PASS
                    || StrUtil.isBlank(auth.getIdCardEnc())) {
                throw new StatusCodeException(StatusCode.REAL_NAME_REQUIRED, "请先完成账号实名认证后再一键添加");
            }
            idCard = idCardCryptoUtils.decrypt(auth.getIdCardEnc());
            if (StrUtil.isBlank(request.getName())) {
                request.setName(auth.getRealName());
            }
        }
        if (StrUtil.isBlank(idCard)) {
            throw new StatusCodeException(StatusCode.PARAM_ERROR, "请填写 18 位身份证号，或先完成实名认证后一键添加");
        }
        if (request.getIdType() == null || request.getIdType() == 1) {
            if (!RegexUtils.isIdCardValid(idCard)) {
                throw new StatusCodeException(StatusCode.ID_CARD_INVALID, "证件号格式不正确，请核对 18 位身份证号");
            }
        }

        long count = audienceMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Audience>()
                        .eq(Audience::getUserId, userId));
        if (count >= MAX_AUDIENCE) {
            throw new StatusCodeException(StatusCode.AUDIENCE_LIMIT_EXCEEDED);
        }

        String hash = idCardCryptoUtils.hash(idCard);
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
        audience.setIdCardEnc(idCardCryptoUtils.encrypt(idCard));
        audience.setIdCardMask(idCardCryptoUtils.mask(idCard));
        audience.setIdCardHash(hash);
        audience.setPhone(request.getPhone());
        audience.setIsDefault(count == 0 ? 1 : 0);
        audience.setAuthStatus(AUDIENCE_AUTH_PASS);
        audience.setCreateTime(LocalDateTime.now());
        audience.setUpdateTime(LocalDateTime.now());
        audienceMapper.insert(audience);

        return list(userId);
    }

    @Override
    public AudienceListVo update(Long userId, Long id, AudienceUpdateRequest request) {
        Audience audience = loadOwned(userId, id);
        if (request == null || StrUtil.isBlank(request.getName())) {
            throw new StatusCodeException(StatusCode.PARAM_ERROR);
        }
        if (StrUtil.isNotBlank(request.getIdCard())) {
            if (request.getIdType() == null || request.getIdType() == 1) {
                if (!RegexUtils.isIdCardValid(request.getIdCard())) {
                    throw new StatusCodeException(StatusCode.ID_CARD_INVALID);
                }
            }
            String hash = idCardCryptoUtils.hash(request.getIdCard());
            long dup = audienceMapper.selectCount(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Audience>()
                            .eq(Audience::getUserId, userId)
                            .eq(Audience::getIdCardHash, hash)
                            .ne(Audience::getId, id));
            if (dup > 0) {
                throw new StatusCodeException(StatusCode.AUDIENCE_DUPLICATED);
            }
            audience.setIdCardEnc(idCardCryptoUtils.encrypt(request.getIdCard()));
            audience.setIdCardMask(idCardCryptoUtils.mask(request.getIdCard()));
            audience.setIdCardHash(hash);
        }
        audience.setName(request.getName().trim());
        if (request.getIdType() != null) {
            audience.setIdType(request.getIdType());
        }
        if (request.getPhone() != null) {
            audience.setPhone(request.getPhone());
        }
        audience.setAuthStatus(AUDIENCE_AUTH_PASS);
        audience.setUpdateTime(LocalDateTime.now());
        audienceMapper.updateById(audience);
        return list(userId);
    }

    private static String audienceAuthText(Integer status) {
        if (status == null || status == 0) {
            return "未认证";
        }
        if (status == AUDIENCE_AUTH_PASS || status == 1) {
            return "已认证";
        }
        return "未认证";
    }

    @Override
    public AudienceListVo delete(Long userId, Long id) {
        Audience audience = loadOwned(userId, id);
        if (audience.getCreateTime() != null
                && Duration.between(audience.getCreateTime(), LocalDateTime.now()).toHours() < 24
                && Objects.equals(audience.getAuthStatus(), 1)) {
            throw new StatusCodeException(StatusCode.AUDIENCE_DELETE_FORBIDDEN);
        }
        long refs = orderAudienceMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<OrderAudience>()
                        .eq(OrderAudience::getAudienceId, id));
        if (refs > 0) {
            audienceMapper.deleteById(id);
        } else {
            audienceMapper.deleteById(id);
        }
        if (Objects.equals(audience.getIsDefault(), 1)) {
            Audience next = audienceMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Audience>()
                            .eq(Audience::getUserId, userId)
                            .orderByDesc(Audience::getLastUsedTime)
                            .last("LIMIT 1"));
            if (next != null) {
                next.setIsDefault(1);
                audienceMapper.updateById(next);
            }
        }
        return list(userId);
    }

    @Override
    public AudienceListVo setDefault(Long userId, Long id) {
        loadOwned(userId, id);
        Audience clear = new Audience();
        clear.setIsDefault(0);
        audienceMapper.update(clear, new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Audience>()
                .eq(Audience::getUserId, userId));
        Audience audience = audienceMapper.selectById(id);
        audience.setIsDefault(1);
        audience.setUpdateTime(LocalDateTime.now());
        audienceMapper.updateById(audience);
        return list(userId);
    }

    private Audience loadOwned(Long userId, Long id) {
        Audience audience = audienceMapper.selectById(id);
        if (audience == null || !userId.equals(audience.getUserId())) {
            throw new StatusCodeException(StatusCode.AUDIENCE_NOT_FOUND);
        }
        return audience;
    }
}