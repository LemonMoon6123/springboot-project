package com.livehouse.utils;

import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.digest.DigestUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * 票务安全工具类
 * 用于生成和验证票务签名，防止伪造
 */
@Slf4j
@Component
public class TicketSecurityUtils {

    /**
     * 签名密钥，从配置文件读取
     */
    @Value("${ticket.security.secret-key:livehouse-ticket-secret-2024}")
    private String secretKey;

    /**
     * 二维码有效期（分钟）
     */
    @Value("${ticket.security.qr-validity-minutes:30}")
    private Integer qrValidityMinutes;

    /**
     * 生成票务签名
     * 
     * @param ticketId   票务ID
     * @param ticketCode 票务编码
     * @param showId     演出ID
     * @param userId     用户ID
     * @param timestamp  时间戳
     * @return 数字签名
     */
    public String generateTicketSignature(Long ticketId, String ticketCode, Long showId, 
                                         Long userId, Long timestamp) {
        String data = String.format("%d-%s-%d-%d-%d-%s", 
                                   ticketId, ticketCode, showId, userId, timestamp, secretKey);
        return DigestUtil.sha256Hex(data);
    }

    /**
     * 验证票务签名
     * 
     * @param ticketId   票务ID
     * @param ticketCode 票务编码
     * @param showId     演出ID
     * @param userId     用户ID
     * @param timestamp  时间戳
     * @param signature  待验证的签名
     * @return 签名是否有效
     */
    public boolean verifyTicketSignature(Long ticketId, String ticketCode, Long showId, 
                                       Long userId, Long timestamp, String signature) {
        String expectedSignature = generateTicketSignature(ticketId, ticketCode, showId, userId, timestamp);
        return expectedSignature.equals(signature);
    }

    /**
     * 生成当前时间戳
     * 
     * @return 当前时间戳（秒）
     */
    public Long generateCurrentTimestamp() {
        return LocalDateTime.now().toEpochSecond(ZoneOffset.of("+8"));
    }

    /**
     * 验证时间戳是否在有效期内
     * 
     * @param timestamp 待验证的时间戳
     * @return 时间戳是否有效
     */
    public boolean isTimestampValid(Long timestamp) {
        long currentTimestamp = generateCurrentTimestamp();
        long validitySeconds = qrValidityMinutes * 60L;
        
        // 检查时间戳是否在有效期内（允许前后误差）
        long timeDiff = Math.abs(currentTimestamp - timestamp);
        return timeDiff <= validitySeconds;
    }

    /**
     * 生成防重放攻击的nonce值
     * 
     * @param ticketId 票务ID
     * @return nonce值
     */
    public String generateNonce(Long ticketId) {
        String data = ticketId + "-" + System.nanoTime() + "-" + secretKey;
        return DigestUtil.md5Hex(data).substring(0, 16);
    }

    /**
     * 生成完整的二维码数据
     * 包含所有必要的安全信息
     * 
     * @param ticketId   票务ID
     * @param ticketCode 票务编码
     * @param showId     演出ID
     * @param userId     用户ID
     * @return 二维码内容
     */
    public String generateSecureQRData(Long ticketId, String ticketCode, Long showId, Long userId) {
        Long timestamp = generateCurrentTimestamp();
        String nonce = generateNonce(ticketId);
        String signature = generateTicketSignature(ticketId, ticketCode, showId, userId, timestamp);
        
        return QRCodeGenerator.generateTicketQRContent(ticketId, ticketCode, showId, userId, timestamp, signature);
    }

    /**
     * 验证二维码数据的完整性和有效性
     * 
     * @param ticketId   票务ID
     * @param ticketCode 票务编码
     * @param showId     演出ID
     * @param userId     用户ID
     * @param timestamp  时间戳
     * @param signature  签名
     * @return 验证结果
     */
    public TicketVerificationResult verifyQRData(Long ticketId, String ticketCode, Long showId, 
                                               Long userId, Long timestamp, String signature) {
        try {
            // 1. 验证时间戳
            if (!isTimestampValid(timestamp)) {
                return TicketVerificationResult.builder()
                        .valid(false)
                        .errorCode("EXPIRED")
                        .errorMessage("二维码已过期")
                        .build();
            }

            // 2. 验证签名
            if (!verifyTicketSignature(ticketId, ticketCode, showId, userId, timestamp, signature)) {
                return TicketVerificationResult.builder()
                        .valid(false)
                        .errorCode("INVALID_SIGNATURE")
                        .errorMessage("签名验证失败，可能是伪造票")
                        .build();
            }

            return TicketVerificationResult.builder()
                    .valid(true)
                    .errorCode("SUCCESS")
                    .errorMessage("验证成功")
                    .build();

        } catch (Exception e) {
            log.error("验证二维码数据时发生异常", e);
            return TicketVerificationResult.builder()
                    .valid(false)
                    .errorCode("SYSTEM_ERROR")
                    .errorMessage("系统验证错误")
                    .build();
        }
    }

    /**
     * 票务验证结果
     */
    public static class TicketVerificationResult {
        private boolean valid;
        private String errorCode;
        private String errorMessage;

        private TicketVerificationResult(Builder builder) {
            this.valid = builder.valid;
            this.errorCode = builder.errorCode;
            this.errorMessage = builder.errorMessage;
        }

        public static Builder builder() {
            return new Builder();
        }

        public boolean isValid() {
            return valid;
        }

        public String getErrorCode() {
            return errorCode;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public static class Builder {
            private boolean valid;
            private String errorCode;
            private String errorMessage;

            public Builder valid(boolean valid) {
                this.valid = valid;
                return this;
            }

            public Builder errorCode(String errorCode) {
                this.errorCode = errorCode;
                return this;
            }

            public Builder errorMessage(String errorMessage) {
                this.errorMessage = errorMessage;
                return this;
            }

            public TicketVerificationResult build() {
                return new TicketVerificationResult(this);
            }
        }
    }
}