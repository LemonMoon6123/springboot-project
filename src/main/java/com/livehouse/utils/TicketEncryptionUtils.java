package com.livehouse.utils;

import cn.hutool.crypto.symmetric.AES;
import cn.hutool.core.util.StrUtil;
import com.livehouse.exception.CustomException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;

/**
 * 电子票加密工具类
 * 用于对票务验证码进行加密和解密，增强安全性
 */
@Slf4j
@Component
public class TicketEncryptionUtils {

    @Value("${livehouse.ticket.encryption-key:LiveHouse2024SecretKey!@#$%^&*()}")
    private String encryptionKey;

    private AES aes;

    @PostConstruct
    public void init() {
        // 确保密钥长度为32字节（256位）
        String key = padOrTruncateKey(encryptionKey);
        this.aes = new AES(key.getBytes(StandardCharsets.UTF_8));
        log.info("票务加密工具初始化成功");
    }

    /**
     * 加密票务验证码
     * 
     * @param originalTicketCode 原始票务验证码（如：ET123456789）
     * @return 加密后的票务验证码（Base64编码）
     */
    public String encryptTicketCode(String originalTicketCode) {
        if (StrUtil.isBlank(originalTicketCode)) {
            throw new IllegalArgumentException("票务验证码不能为空");
        }
        
        try {
            // 加密后转为Base64，确保可以作为字符串传输
            String encrypted = aes.encryptBase64(originalTicketCode);
            log.debug("票务验证码加密成功，原始长度：{}，加密后长度：{}", 
                     originalTicketCode.length(), encrypted.length());
            return encrypted;
        } catch (Exception e) {
            log.error("票务验证码加密失败，原始码：{}", originalTicketCode);
            throw new CustomException("票务验证码加密失败");
        }
    }

    /**
     * 解密票务验证码
     * 
     * @param encryptedTicketCode 加密后的票务验证码
     * @return 原始票务验证码
     */
    public String decryptTicketCode(String encryptedTicketCode) {
        if (StrUtil.isBlank(encryptedTicketCode)) {
            throw new CustomException("加密票务验证码不能为空");
        }
        
        try {
            String decrypted = aes.decryptStr(encryptedTicketCode);
            log.debug("票务验证码解密成功，加密长度：{}，解密后长度：{}", encryptedTicketCode.length(), decrypted.length());
            return decrypted;
        } catch (Exception e) {
            log.error("票务验证码解密失败，加密码：{}", encryptedTicketCode, e);
            throw new CustomException("票务验证码解密失败，可能是伪造票据");
        }
    }

    /**
     * 从加密的票务验证码计算BitMap偏移量
     * 
     * @param encryptedTicketCode 加密后的票务验证码
     * @return BitMap偏移量（32位正整数）
     */
    public long calculateBitMapOffset(String encryptedTicketCode) {
        if (StrUtil.isBlank(encryptedTicketCode)) {
            throw new RuntimeException("加密票务验证码不能为空");
        }
        
        try {
            // 1. 解密得到原始票号
            String originalTicketCode = decryptTicketCode(encryptedTicketCode);
            
            // 2. 从原始票号中提取ticketId (ET123456789 → 123456789)
            Long ticketId = extractTicketId(originalTicketCode);
            
            // 3. 对ticketId进行唯一变换（保证一对一映射，无冲突）
            long uniqueOffset = transformTicketId(ticketId);
            
            // 4. 取低32位作为最终偏移量
            long offset = uniqueOffset & 0xFFFFFFFFL;
            
            log.debug("计算唯一BitMap偏移量，原始票号：{}，ticketId：{}，变换后：{}，最终偏移量：{}", 
                     originalTicketCode, ticketId, uniqueOffset, offset);
            
            return offset;
        } catch (Exception e) {
            log.error("计算BitMap偏移量失败，加密码：{}", 
                     encryptedTicketCode.substring(0, Math.min(10, encryptedTicketCode.length())) + "...", e);
            throw new RuntimeException("计算BitMap偏移量失败", e);
        }
    }

    /**
     * 从原始票号中提取ticketId
     */
    private Long extractTicketId(String originalTicketCode) {
        if (!originalTicketCode.startsWith("ET") || originalTicketCode.length() < 5) {
            throw new IllegalArgumentException("票号格式错误: " + originalTicketCode);
        }
        
        try {
            // ET123456789 → 123456789
            return Long.parseLong(originalTicketCode.substring(2));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("无效的票号格式: " + originalTicketCode, e);
        }
    }

    /**
     * 对ticketId进行唯一变换
     * 使用数学变换保证一对一映射（无冲突）+ 增加随机性（不可预测）
     */
    private long transformTicketId(Long ticketId) {
        // 常数选择：大质数，确保良好的分布特性
        final long PRIME_MULTIPLIER = 0x9E3779B97F4A7C15L;  // 黄金比例相关的大质数
        final long XOR_SALT = 0x123456789ABCDEFL;          // 固定异或盐值
        
        // 可逆变换公式：(ticketId ⊕ salt) × prime
        // 这种变换具有以下特性：
        // 1. 一对一映射：不同的ticketId得到不同的结果（无冲突）
        // 2. 伪随机性：结果看起来是随机的（不可预测）
        // 3. 可逆性：理论上可以逆向计算（但需要知道算法和常数）
        long transformed = (ticketId ^ XOR_SALT) * PRIME_MULTIPLIER;
        
        log.debug("ticketId变换：{} → {}", ticketId, transformed);
        
        return transformed;
    }

    /**
     * 验证加密票务验证码的有效性
     * 
     * @param encryptedTicketCode 加密后的票务验证码
     * @return 验证结果
     */
    public TicketValidationResult validateEncryptedTicketCode(String encryptedTicketCode) {
        try {
            if (StrUtil.isBlank(encryptedTicketCode)) {
                return TicketValidationResult.failure("EMPTY_CODE", "票务验证码为空");
            }
            
            // 尝试解密
            String originalCode = decryptTicketCode(encryptedTicketCode);
            
            // 验证原始码格式
            if (!originalCode.startsWith("ET") || originalCode.length() < 5) {
                return TicketValidationResult.failure("INVALID_FORMAT", "票务验证码格式错误");
            }
            
            return TicketValidationResult.success(originalCode);
            
        } catch (Exception e) {
            log.warn("票务验证码验证失败：{}", e.getMessage());
            return TicketValidationResult.failure("DECRYPT_ERROR", "票务验证码无效或已损坏");
        }
    }

    /**
     * 调整密钥长度为32字节
     */
    private String padOrTruncateKey(String key) {
        if (key == null || key.isEmpty()) {
            key = "LiveHouse2024SecretKey!@#$%^&*()";
        }
        
        if (key.length() > 32) {
            return key.substring(0, 32);
        } else if (key.length() < 32) {
            StringBuilder sb = new StringBuilder(key);
            while (sb.length() < 32) {
                sb.append("0");
            }
            return sb.toString();
        }
        
        return key;
    }

    /**
     * 票务验证结果
     */
    public static class TicketValidationResult {
        private final boolean valid;
        private final String originalTicketCode;
        private final String errorCode;
        private final String errorMessage;

        private TicketValidationResult(boolean valid, String originalTicketCode, String errorCode, String errorMessage) {
            this.valid = valid;
            this.originalTicketCode = originalTicketCode;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
        }

        public static TicketValidationResult success(String originalTicketCode) {
            return new TicketValidationResult(true, originalTicketCode, "SUCCESS", "验证成功");
        }

        public static TicketValidationResult failure(String errorCode, String errorMessage) {
            return new TicketValidationResult(false, null, errorCode, errorMessage);
        }

        // Getters
        public boolean isValid() { return valid; }
        public String getOriginalTicketCode() { return originalTicketCode; }
        public String getErrorCode() { return errorCode; }
        public String getErrorMessage() { return errorMessage; }
    }
}