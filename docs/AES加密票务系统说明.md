# AES加密票务系统说明

## 🔐 改进内容

### 之前的问题
- 票号是明文的 `ET123456789`，容易被伪造
- BitMap偏移量基于ticketId，可预测

### 现在的改进
- 票号用AES加密，变成 `encrypted_string`，无法伪造
- BitMap偏移量基于加密字符串的哈希，更随机更安全

## 📝 代码简要说明

### 1. 生成电子票时
```java
// 原来：直接存储明文票号
String ticketCode = "ET" + ticketId;
ticket.setVerifyCode(ticketCode);

// 现在：先加密再存储
String originalTicketCode = "ET" + ticketId;                          // 生成原始票号
String encryptedTicketCode = ticketEncryptionUtils.encryptTicketCode(originalTicketCode);  // 加密
ticket.setVerifyCode(encryptedTicketCode);                            // 存储加密后的
```

### 2. 验票时
```java
// 现在的验票流程（简化版）：
1. 接收加密的票号
2. 先解密 → 得到原始票号（如 ET123456789）
3. 验证格式是否正确（必须以ET开头）
4. 用加密票号查询数据库
5. 用加密票号计算BitMap位置（更安全）
6. 正常的核销流程
```

## 🛡️ 安全性提升

### 原来的安全问题
- 明文票号：`ET123456789` → 容易被猜测和伪造
- 可预测的BitMap位置：直接用ticketId

### 现在的安全性
- 加密票号：`3xB9kL2mP8qR5tY...` → 无法伪造
- 随机BitMap位置：基于加密字符串哈希

## 🧪 测试步骤

1. **生成新票**：购票后获得加密的票号
2. **验票测试**：
   - 用加密票号验票 → 成功
   - 用伪造票号验票 → 失败（解密失败）
   - 用已核销票号验票 → 失败（已使用）

## 🎯 关键代码文件

- `TicketEncryptionUtils.java` → 加密/解密工具
- `ElectronicTicketServiceImpl.java` → 主要业务逻辑
- `generateElectronicTickets()` → 生成时加密
- `verifyTicket()` → 验票时解密

## 💡 配置说明

在 `application.yaml` 中可以配置加密密钥：
```yaml
livehouse:
  ticket:
    encryption-key: "your-32-character-secret-key-here"
```

## 🔍 日志查看

启动应用后，查看日志：
- `票号解密成功，原始票号：ET123456789` → 解密正常
- `票务验证码验证失败` → 可能是伪造票据
- `电子票核销成功` → 验票成功

这样，你的票务系统就有了真正的防伪功能！🚀