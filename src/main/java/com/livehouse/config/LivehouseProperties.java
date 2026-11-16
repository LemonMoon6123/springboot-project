package com.livehouse.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 业务配置（规格附录 D.2），禁止在业务代码中硬编码超时/服务费/退票天数。
 */
@Data
@Component
@ConfigurationProperties(prefix = "livehouse")
public class LivehouseProperties {

    private Pay pay = new Pay();
    private Order order = new Order();
    private Credit credit = new Credit();
    private Upload upload = new Upload();
    private Ticket ticket = new Ticket();
    private WxLogin wxLogin = new WxLogin();

    @Data
    public static class Pay {
        /** 本地联调 mock 支付开关 */
        private boolean mockEnabled;
        private Wx wx = new Wx();
    }

    @Data
    public static class Wx {
        private String appId;
        private String mchId;
        private String apiV3Key;
        private String certSerialNo;
        private String privateKeyPath;
        private String platformCertPath;
        private String notifyUrl;
    }

    @Data
    public static class Order {
        private int payTimeoutMinutes = 15;
        private BigDecimal serviceFeePerTicket = new BigDecimal("6.00");
        private int refundDeadlineDays = 7;
    }

    @Data
    public static class Credit {
        private BigDecimal ratePerYuan = new BigDecimal("0.05");
        private int issueDelayHours = 24;
        private int expireMonths = 24;
    }

    @Data
    public static class Upload {
        private String dir = "frontend-livehouse/uploads";
        private String urlPrefix = "/uploads/";
    }

    @Data
    public static class Ticket {
        private String encryptionKey = "LiveHouse2024SecretKey!@#$%^&*()";
    }

    @Data
    public static class WxLogin {
        /** dev：任意 code 映射为固定 openid，无需微信密钥 */
        private boolean mockOpenid = true;
        private String mockOpenidPrefix = "dev_wx_openid_";
    }
}