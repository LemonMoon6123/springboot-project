package com.livehouse.controller;

import com.livehouse.dto.Result;
import com.livehouse.service.IElectronicTicketService;
import com.livehouse.utils.QRCodeGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;

/**
 * 移动端票务控制器
 * 专门为移动设备、PDA扫码枪、第三方验票设备提供API
 */
@Slf4j
@RestController
@RequestMapping("/mobile/ticket")
@CrossOrigin(origins = "*") // 允许跨域访问，适配移动端和第三方设备
public class MobileTicketController {

    @Autowired
    private IElectronicTicketService electronicTicketService;

    /**
     * 移动端扫码验票接口
     * 专为移动端App设计，返回详细的验票信息
     * 
     * @param request 验票请求
     * @param httpRequest HTTP请求（用于获取设备信息）
     * @return 验票结果
     */
    @PostMapping("/scan-verify")
    public Result scanAndVerify(@RequestBody MobileScanRequest request, HttpServletRequest httpRequest) {
        String userAgent = httpRequest.getHeader("User-Agent");
        String clientIp = getClientIpAddress(httpRequest);
        
        log.info("移动端扫码验票请求，设备类型：{}，IP：{}，操作员：{}", 
                 request.getDeviceType(), clientIp, request.getOperatorId());

        // 记录验票操作日志
        logVerificationAttempt(request, userAgent, clientIp);

        return electronicTicketService.verifyTicketByQRCode(request.getQrCodeContent());
    }

    /**
     * PDA设备验票接口
     * 专为现场PDA扫码枪设计，返回简化的验票信息
     * 
     * @param request 验票请求
     * @return 简化的验票结果
     */
    @PostMapping("/pda-verify")
    public Result pdaVerify(@RequestBody PDAVerifyRequest request) {
        log.info("PDA设备验票请求，设备ID：{}，操作员：{}", request.getDeviceId(), request.getOperatorId());

        Result result = electronicTicketService.verifyTicketByQRCode(request.getQrCodeContent());
        
        // 为PDA设备返回简化的响应
        if (result.getSuccess()) {
            return Result.ok(new PDAVerifyResponse("SUCCESS", "验票成功", LocalDateTime.now()));
        } else {
            return Result.fail(result.getErrorMsg());
        }
    }

    /**
     * 第三方设备验票接口
     * 支持各种第三方验票设备的集成
     * 
     * @param apiKey API密钥（用于设备认证）
     * @param request 验票请求
     * @return 标准化的验票结果
     */
    @PostMapping("/third-party-verify")
    public Result thirdPartyVerify(@RequestHeader("X-API-Key") String apiKey,
                                   @RequestBody ThirdPartyVerifyRequest request) {
        log.info("第三方设备验票请求，API Key：{}，设备：{}，厂商：{}", 
                 maskApiKey(apiKey), request.getDeviceModel(), request.getVendor());

        // TODO: 验证API Key
        if (!isValidApiKey(apiKey)) {
            return Result.fail("无效的API密钥");
        }

        Result result = electronicTicketService.verifyTicketByQRCode(request.getQrCodeData());
        
        // 返回标准化的第三方响应格式
        ThirdPartyVerifyResponse response = new ThirdPartyVerifyResponse();
        response.setSuccess(result.getSuccess());
        response.setMessage(result.getSuccess() ? "VERIFY_SUCCESS" : result.getErrorMsg());
        response.setTimestamp(System.currentTimeMillis());
        response.setDeviceId(request.getDeviceId());
        
        return Result.ok(response);
    }

    /**
     * 批量验票接口（适用于快速通道）
     * 支持同时验证多张票
     * 
     * @param request 批量验票请求
     * @return 批量验票结果
     */
    @PostMapping("/batch-verify")
    public Result batchVerify(@RequestBody BatchVerifyRequest request) {
        log.info("批量验票请求，票数：{}，操作员：{}", request.getQrCodes().size(), request.getOperatorId());

        BatchVerifyResponse response = new BatchVerifyResponse();
        
        for (String qrCode : request.getQrCodes()) {
            try {
                Result result = electronicTicketService.verifyTicketByQRCode(qrCode);
                
                BatchVerifyResponse.TicketResult ticketResult = new BatchVerifyResponse.TicketResult();
                ticketResult.setQrCode(qrCode.substring(0, Math.min(50, qrCode.length())) + "..."); // 截断显示
                ticketResult.setSuccess(result.getSuccess());
                ticketResult.setMessage(result.getSuccess() ? "成功" : result.getErrorMsg());
                
                response.addResult(ticketResult);
                
            } catch (Exception e) {
                log.error("批量验票中单票处理失败", e);
                
                BatchVerifyResponse.TicketResult ticketResult = new BatchVerifyResponse.TicketResult();
                ticketResult.setQrCode(qrCode.substring(0, Math.min(50, qrCode.length())) + "...");
                ticketResult.setSuccess(false);
                ticketResult.setMessage("处理异常");
                
                response.addResult(ticketResult);
            }
        }
        
        return Result.ok(response);
    }

    /**
     * 解析二维码接口（仅解析不验票）
     * 用于设备测试或二维码内容验证
     * 
     * @param request 二维码解析请求
     * @return 解析结果
     */
    @PostMapping("/parse-qr")
    public Result parseQRCode(@RequestBody ParseQRRequest request) {
        log.info("二维码解析请求");
        
        try {
            // 如果是图片形式的二维码，先解析出内容
            String qrContent = request.getQrCodeContent();
            if (request.isImageFormat()) {
                qrContent = QRCodeGenerator.decodeQRCodeFromBase64(request.getQrCodeContent());
            }
            
            // 返回解析后的内容（不进行实际验票）
            return Result.ok(new ParseQRResponse(qrContent, "解析成功"));
            
        } catch (Exception e) {
            log.error("二维码解析失败", e);
            return Result.fail("二维码解析失败：" + e.getMessage());
        }
    }

    /**
     * 获取客户端真实IP地址
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            return xForwardedFor.split(",")[0];
        }
        
        String xRealIP = request.getHeader("X-Real-IP");
        if (xRealIP != null && !xRealIP.isEmpty() && !"unknown".equalsIgnoreCase(xRealIP)) {
            return xRealIP;
        }
        
        return request.getRemoteAddr();
    }

    /**
     * 记录验票操作日志
     */
    private void logVerificationAttempt(MobileScanRequest request, String userAgent, String clientIp) {
        // TODO: 记录到操作日志表
        log.info("验票操作记录 - 操作员：{}，设备：{}，IP：{}，UserAgent：{}", 
                 request.getOperatorId(), request.getDeviceType(), clientIp, userAgent);
    }

    /**
     * 掩码API Key用于日志记录
     */
    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() < 8) {
            return "****";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }

    /**
     * 验证API Key的有效性
     */
    private boolean isValidApiKey(String apiKey) {
        // TODO: 实现API Key验证逻辑
        // 可以从数据库或配置文件中验证
        return apiKey != null && apiKey.length() > 10;
    }

    // ============ DTO类定义 ============

    public static class MobileScanRequest {
        private String qrCodeContent;
        private String deviceType;
        private String operatorId;
        private String location; // 验票地点

        // Getters and Setters
        public String getQrCodeContent() { return qrCodeContent; }
        public void setQrCodeContent(String qrCodeContent) { this.qrCodeContent = qrCodeContent; }
        
        public String getDeviceType() { return deviceType; }
        public void setDeviceType(String deviceType) { this.deviceType = deviceType; }
        
        public String getOperatorId() { return operatorId; }
        public void setOperatorId(String operatorId) { this.operatorId = operatorId; }
        
        public String getLocation() { return location; }
        public void setLocation(String location) { this.location = location; }
    }

    public static class PDAVerifyRequest {
        private String qrCodeContent;
        private String deviceId;
        private String operatorId;

        // Getters and Setters
        public String getQrCodeContent() { return qrCodeContent; }
        public void setQrCodeContent(String qrCodeContent) { this.qrCodeContent = qrCodeContent; }
        
        public String getDeviceId() { return deviceId; }
        public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
        
        public String getOperatorId() { return operatorId; }
        public void setOperatorId(String operatorId) { this.operatorId = operatorId; }
    }

    public static class PDAVerifyResponse {
        private String status;
        private String message;
        private LocalDateTime timestamp;

        public PDAVerifyResponse(String status, String message, LocalDateTime timestamp) {
            this.status = status;
            this.message = message;
            this.timestamp = timestamp;
        }

        // Getters and Setters
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    }

    public static class ThirdPartyVerifyRequest {
        private String qrCodeData;
        private String deviceId;
        private String deviceModel;
        private String vendor;
        private String firmwareVersion;

        // Getters and Setters
        public String getQrCodeData() { return qrCodeData; }
        public void setQrCodeData(String qrCodeData) { this.qrCodeData = qrCodeData; }
        
        public String getDeviceId() { return deviceId; }
        public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
        
        public String getDeviceModel() { return deviceModel; }
        public void setDeviceModel(String deviceModel) { this.deviceModel = deviceModel; }
        
        public String getVendor() { return vendor; }
        public void setVendor(String vendor) { this.vendor = vendor; }
        
        public String getFirmwareVersion() { return firmwareVersion; }
        public void setFirmwareVersion(String firmwareVersion) { this.firmwareVersion = firmwareVersion; }
    }

    public static class ThirdPartyVerifyResponse {
        private boolean success;
        private String message;
        private long timestamp;
        private String deviceId;

        // Getters and Setters
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
        
        public String getDeviceId() { return deviceId; }
        public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    }

    // 其他DTO类省略...
    public static class BatchVerifyRequest {
        private java.util.List<String> qrCodes;
        private String operatorId;

        public java.util.List<String> getQrCodes() { return qrCodes; }
        public void setQrCodes(java.util.List<String> qrCodes) { this.qrCodes = qrCodes; }
        
        public String getOperatorId() { return operatorId; }
        public void setOperatorId(String operatorId) { this.operatorId = operatorId; }
    }

    public static class BatchVerifyResponse {
        private java.util.List<TicketResult> results = new java.util.ArrayList<>();
        private int totalCount;
        private int successCount;
        private int failCount;

        public void addResult(TicketResult result) {
            results.add(result);
            totalCount++;
            if (result.isSuccess()) {
                successCount++;
            } else {
                failCount++;
            }
        }

        // Getters and Setters
        public java.util.List<TicketResult> getResults() { return results; }
        public int getTotalCount() { return totalCount; }
        public int getSuccessCount() { return successCount; }
        public int getFailCount() { return failCount; }

        public static class TicketResult {
            private String qrCode;
            private boolean success;
            private String message;

            // Getters and Setters
            public String getQrCode() { return qrCode; }
            public void setQrCode(String qrCode) { this.qrCode = qrCode; }
            
            public boolean isSuccess() { return success; }
            public void setSuccess(boolean success) { this.success = success; }
            
            public String getMessage() { return message; }
            public void setMessage(String message) { this.message = message; }
        }
    }

    public static class ParseQRRequest {
        private String qrCodeContent;
        private boolean imageFormat;

        // Getters and Setters
        public String getQrCodeContent() { return qrCodeContent; }
        public void setQrCodeContent(String qrCodeContent) { this.qrCodeContent = qrCodeContent; }
        
        public boolean isImageFormat() { return imageFormat; }
        public void setImageFormat(boolean imageFormat) { this.imageFormat = imageFormat; }
    }

    public static class ParseQRResponse {
        private String content;
        private String message;

        public ParseQRResponse(String content, String message) {
            this.content = content;
            this.message = message;
        }

        // Getters and Setters
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}