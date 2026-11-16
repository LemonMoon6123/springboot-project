package com.livehouse.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.crypto.symmetric.AES;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;

/**
 * 观演人证件：AES 存储、脱敏展示、SHA256 去重。
 */
@Component
public class IdCardCryptoUtils {

    @Value("${livehouse.ticket.encryption-key:LiveHouse2024SecretKey!@#$%^&*()}")
    private String encryptionKey;

    private AES aes;

    @PostConstruct
    public void init() {
        String key = padOrTruncateKey(encryptionKey);
        aes = new AES(key.getBytes(StandardCharsets.UTF_8));
    }

    public String encrypt(String plain) {
        return aes.encryptBase64(plain);
    }

    public String hash(String plain) {
        return DigestUtil.sha256Hex(plain);
    }

    public String mask(String plain) {
        if (StrUtil.isBlank(plain) || plain.length() < 8) {
            return "****";
        }
        int len = plain.length();
        int keepHead = Math.min(4, len - 4);
        int keepTail = Math.min(4, len - keepHead);
        return plain.substring(0, keepHead) + "********" + plain.substring(len - keepTail);
    }

    private static String padOrTruncateKey(String key) {
        if (key == null) {
            key = "";
        }
        if (key.length() >= 32) {
            return key.substring(0, 32);
        }
        StringBuilder sb = new StringBuilder(key);
        while (sb.length() < 32) {
            sb.append('0');
        }
        return sb.toString();
    }
}