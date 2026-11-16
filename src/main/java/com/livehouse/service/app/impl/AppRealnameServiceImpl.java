package com.livehouse.service.app.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.livehouse.entity.RealNameAuth;
import com.livehouse.entity.StatusCode;
import com.livehouse.exception.StatusCodeException;
import com.livehouse.mapper.RealNameAuthMapper;
import com.livehouse.service.app.AppRealnameService;
import com.livehouse.utils.IdCardCryptoUtils;
import com.livehouse.utils.RegexUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AppRealnameServiceImpl implements AppRealnameService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Resource
    private RealNameAuthMapper realNameAuthMapper;
    @Resource
    private IdCardCryptoUtils idCardCryptoUtils;

    @Override
    public Map<String, Object> get(Long userId) {
        RealNameAuth auth = realNameAuthMapper.selectOne(new LambdaQueryWrapper<RealNameAuth>()
                .eq(RealNameAuth::getUserId, userId));
        Map<String, Object> data = new LinkedHashMap<>();
        if (auth == null || auth.getAuthStatus() == null || auth.getAuthStatus() == RealNameAuth.STATUS_NONE) {
            data.put("authStatus", 0);
            data.put("authStatusText", "未认证");
            data.put("canModify", false);
            return data;
        }
        data.put("authStatus", auth.getAuthStatus());
        data.put("authStatusText", authText(auth.getAuthStatus()));
        data.put("realName", auth.getRealName());
        data.put("idTypeText", idTypeText(auth.getIdType()));
        data.put("idCardMask", auth.getIdCardMask());
        data.put("phone", maskPhone(auth.getPhone()));
        data.put("authTime", auth.getAuthTime() == null ? null : auth.getAuthTime().format(DT));
        data.put("authChannel", auth.getAuthChannel());
        data.put("canModify", false);
        return data;
    }

    @Override
    @Transactional
    public Map<String, Object> submit(Long userId, String realName, Integer idType, String idCard, String phone) {
        if (realName == null || idCard == null) {
            throw new StatusCodeException(StatusCode.PARAM_ERROR);
        }
        if (idType == null || idType == 1) {
            if (!RegexUtils.isIdCardValid(idCard)) {
                throw new StatusCodeException(StatusCode.ID_CARD_INVALID);
            }
        }
        RealNameAuth existing = realNameAuthMapper.selectOne(new LambdaQueryWrapper<RealNameAuth>()
                .eq(RealNameAuth::getUserId, userId));
        if (existing != null && existing.getAuthStatus() != null && existing.getAuthStatus() == RealNameAuth.STATUS_PASS) {
            throw new StatusCodeException(StatusCode.CONFLICT, "已实名认证");
        }

        RealNameAuth auth = existing != null ? existing : new RealNameAuth();
        auth.setUserId(userId);
        auth.setRealName(realName.trim());
        auth.setIdType(idType == null ? 1 : idType);
        auth.setIdCardEnc(idCardCryptoUtils.encrypt(idCard));
        auth.setIdCardMask(idCardCryptoUtils.mask(idCard));
        auth.setPhone(phone);
        auth.setAuthStatus(RealNameAuth.STATUS_PASS);
        auth.setAuthChannel("local");
        auth.setAuthTime(LocalDateTime.now());
        auth.setUpdateTime(LocalDateTime.now());
        if (existing == null) {
            auth.setCreateTime(LocalDateTime.now());
            realNameAuthMapper.insert(auth);
        } else {
            realNameAuthMapper.updateById(auth);
        }
        return get(userId);
    }

    private static String authText(int s) {
        switch (s) {
            case 2: return "已认证";
            case 1: return "审核中";
            case 3: return "已驳回";
            default: return "未认证";
        }
    }

    private static String idTypeText(Integer t) {
        if (t == null || t == 1) {
            return "居民身份证";
        }
        return "其他证件";
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}