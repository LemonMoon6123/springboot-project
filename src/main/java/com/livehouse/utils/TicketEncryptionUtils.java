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

            // 3. 计算确定性唯一偏移量（混入天序号，避免当日count跨天重复导致撞位）
            long offset = calculateUniqueOffset(ticketId);

            log.debug("计算唯一BitMap偏移量，原始票号：{}，ticketId：{}，最终偏移量：{}",
                     originalTicketCode, ticketId, offset);
            
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
     * 计算确定性唯一的BitMap偏移量（旧方案：XOR+乘质数后截断低32位）
     *
     * 旧方案缺陷：XOR与乘法在模2^32下，低32位输出只由低32位输入决定，
     * 时间戳（高32位）对offset零影响 → offset实际只是"当日count"的打乱。
     * 而count按天重置（icr:xxx:yyyy:MM:dd每天从1重计），同一场演出跨天售票时
     * 不同天的同序号票会算出相同offset，共用同一个bit，后核销者被误判"已使用过"。
     *
     * 新方案：offset = 天序号 × 1000000 + 当日count，确定性一一映射，零冲突。
     * - ticketId高32位=秒级时间戳（相对BEGIN_TIMESTAMP），低32位=当日递增count
     * - 同天count唯一、不同天dayIndex不同 → 任意两张票offset必不相同
     * - 容量约束：单日售票量需 < 100万；2^32内可表示约4294天（约11.7年）不越界
     * - bitmap内存随最大offset线性增长（≈offset/8字节），不再被随机打散撑大
     *
     * 酒店每层 100 万个房间，房间号 = count，楼层号 = dayIndex——只要每层入住不超员，任何两个房客的“楼层+房间号”组合必不相同。
     */
    private long calculateUniqueOffset(Long ticketId) {
        long timestamp = ticketId >>> 32;      // 秒级时间戳（相对起始时间）
        long count = ticketId & 0xFFFFFFFFL;   // 当日递增序号
        long dayIndex = timestamp / 86400;     // 天序号
        return dayIndex * 1_000_000L + count;
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