package com.scaffold.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scaffold.common.BusinessException;
import com.scaffold.dto.AccountProfile;
import com.scaffold.dto.LoginRequest;
import com.scaffold.dto.LoginResponse;
import com.scaffold.dto.PasswordChangeRequest;
import com.scaffold.dto.ProfileUpdateRequest;
import com.scaffold.dto.RegisterRequest;
import com.scaffold.entity.Admin;
import com.scaffold.entity.Merchant;
import com.scaffold.entity.User;
import com.scaffold.mapper.AdminMapper;
import com.scaffold.mapper.MerchantMapper;
import com.scaffold.mapper.UserMapper;
import com.scaffold.security.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

@Service
public class AuthService {

    public static final String ACCOUNT_ADMIN = "ADMIN";
    public static final String ACCOUNT_USER = "USER";
    public static final String ACCOUNT_MERCHANT = "MERCHANT";

    @Autowired
    private AdminMapper adminMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private MerchantMapper merchantMapper;
    @Autowired
    private JwtUtils jwtUtils;
    @Autowired
    private RechargeRecordService rechargeRecordService;

    public LoginResponse login(LoginRequest request) {
        Admin admin = adminMapper.selectOne(new LambdaQueryWrapper<Admin>()
                .eq(Admin::getUsername, request.getUsername())
                .last("LIMIT 1"));
        if (admin != null) {
            if (!admin.getPassword().equals(request.getPassword())) {
                throw new BusinessException("用户名或密码错误");
            }
            if (admin.getStatus() != null && admin.getStatus() == 0) {
                throw new BusinessException("账号已被禁用");
            }
            return buildAdminLoginResponse(admin);
        }

        Merchant merchant = merchantMapper.selectOne(new LambdaQueryWrapper<Merchant>()
                .eq(Merchant::getUsername, request.getUsername())
                .last("LIMIT 1"));
        if (merchant != null) {
            if (!merchant.getPassword().equals(request.getPassword())) {
                throw new BusinessException("用户名或密码错误");
            }
            if (merchant.getStatus() != null && merchant.getStatus() == 0) {
                throw new BusinessException("账号已被禁用");
            }
            Integer audit = merchant.getAuditStatus();
            if (audit == null || audit == 0) {
                throw new BusinessException("商家账号待审核");
            }
            if (audit == 2) {
                throw new BusinessException("审核未通过");
            }
            return buildMerchantLoginResponse(merchant);
        }

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername())
                .last("LIMIT 1"));
        if (user == null || !user.getPassword().equals(request.getPassword())) {
            throw new BusinessException("用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException("账号已被禁用");
        }
        return buildUserLoginResponse(user);
    }

    public LoginResponse register(RegisterRequest request) {

        // 去掉用户名首尾多余字符
        String username = request.getUsername().trim();

        // 检查是否是三端用户名中的一个
        if (isUsernameTaken(username)) {
            throw new BusinessException("用户名已存在");
        }

        // 判断角色权限情况，如果权限为空，默认为USER
        String role = StringUtils.hasText(request.getRole()) ? request.getRole().trim().toUpperCase() : ACCOUNT_USER;
        if (!ACCOUNT_USER.equals(role) && !ACCOUNT_MERCHANT.equals(role)) {
            throw new BusinessException("注册角色无效");
        }

        // 如果是商家，做一些基础校验
        if (ACCOUNT_MERCHANT.equals(role)) {
            if (!StringUtils.hasText(request.getShopName())) {
                throw new BusinessException("店铺名称不能为空");
            }
            if (!StringUtils.hasText(request.getLicenseImage())) {
                throw new BusinessException("请上传营业执照");
            }
            if (!StringUtils.hasText(request.getIdCardFront())) {
                throw new BusinessException("请上传身份证正面");
            }
            if (!StringUtils.hasText(request.getIdCardBack())) {
                throw new BusinessException("请上传身份证反面");
            }

            // 封装商家数据
            Merchant merchant = new Merchant();
            merchant.setUsername(username);
            merchant.setPassword(request.getPassword());
            merchant.setNickname(StringUtils.hasText(request.getNickname()) ? request.getNickname().trim() : username);
            merchant.setPhone(StringUtils.hasText(request.getPhone()) ? request.getPhone().trim() : null);
            merchant.setShopName(request.getShopName().trim());
            merchant.setShopDesc(StringUtils.hasText(request.getShopDesc()) ? request.getShopDesc().trim() : null);
            merchant.setLicenseImage(request.getLicenseImage().trim());
            merchant.setIdCardFront(request.getIdCardFront().trim());
            merchant.setIdCardBack(request.getIdCardBack().trim());
            merchant.setStatus(1);
            merchant.setAuditStatus(0);

            // 插入数据库
            merchantMapper.insert(merchant);

            Merchant saved = merchantMapper.selectById(merchant.getId());

            // 统一用该实体类封装数据作为响应结果
            LoginResponse resp = new LoginResponse();

            resp.setToken(null); // 注册成功时没有token
            resp.setUsername(saved.getUsername());
            resp.setNickname(saved.getNickname());
            resp.setRole(ACCOUNT_MERCHANT);
            resp.setAvatar(saved.getAvatar());
            resp.setUserId(saved.getId());
            resp.setAccountType(ACCOUNT_MERCHANT);
            resp.setMessage("注册成功，请等待审核");
            return resp;
        }

        // 否则就是用户，为用户封装数据
        User user = new User();
        user.setUsername(username);
        user.setPassword(request.getPassword());
        user.setNickname(StringUtils.hasText(request.getNickname()) ? request.getNickname().trim() : username);
        user.setPhone(StringUtils.hasText(request.getPhone()) ? request.getPhone().trim() : null);
        user.setStatus(1);
        user.setBalance(0.0);

        // 插入数据
        userMapper.insert(user);

        // 使用JwtUtils工具类构建用户信息
        return buildUserLoginResponse(userMapper.selectById(user.getId()));
    }

    // 按账号类型查询业务实体数据： User Merchant Admin
    public AccountProfile getCurrentUser(Long accountId, String accountType) {
        if (ACCOUNT_ADMIN.equals(accountType)) {
            Admin admin = adminMapper.selectById(accountId);
            if (admin == null) {
                throw new BusinessException(404, "管理员不存在");
            }
            return toAdminProfile(admin);
        }
        if (ACCOUNT_MERCHANT.equals(accountType)) {
            Merchant merchant = merchantMapper.selectById(accountId);
            if (merchant == null) {
                throw new BusinessException(404, "商家不存在");
            }
            return toMerchantProfile(merchant);
        }
        User user = userMapper.selectById(accountId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        return toUserProfile(user);
    }

    // 按账号类型更新个人信息，如果相应字段不为空，就更新对应字段
    public AccountProfile updateProfile(Long accountId, String accountType, ProfileUpdateRequest request) {
        if (ACCOUNT_ADMIN.equals(accountType)) {
            Admin admin = adminMapper.selectById(accountId);
            if (admin == null) {
                throw new BusinessException(404, "管理员不存在");
            }
            if (StringUtils.hasText(request.getNickname())) {
                admin.setNickname(request.getNickname());
            }
            if (request.getAvatar() != null) {
                admin.setAvatar(request.getAvatar());
            }
            if (request.getPhone() != null) {
                admin.setPhone(request.getPhone());
            }

            // 保存相应更新信息到数据库
            adminMapper.updateById(admin);

            // 更新落库后再查出来返回更新后的数据
            return toAdminProfile(adminMapper.selectById(accountId));
        }

        if (ACCOUNT_MERCHANT.equals(accountType)) {
            Merchant merchant = merchantMapper.selectById(accountId);
            if (merchant == null) {
                throw new BusinessException(404, "商家不存在");
            }
            if (StringUtils.hasText(request.getNickname())) {
                merchant.setNickname(request.getNickname());
            }
            if (request.getAvatar() != null) {
                merchant.setAvatar(request.getAvatar());
            }
            if (request.getPhone() != null) {
                merchant.setPhone(request.getPhone());
            }
            if (StringUtils.hasText(request.getShopName())) {
                merchant.setShopName(request.getShopName());
            }
            if (request.getShopDesc() != null) {
                merchant.setShopDesc(request.getShopDesc());
            }
            merchantMapper.updateById(merchant);
            return toMerchantProfile(merchantMapper.selectById(accountId));
        }

        User user = userMapper.selectById(accountId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        if (StringUtils.hasText(request.getNickname())) {
            user.setNickname(request.getNickname());
        }
        if (request.getAvatar() != null) {
            user.setAvatar(request.getAvatar());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        userMapper.updateById(user);
        return toUserProfile(userMapper.selectById(accountId));
    }

    // 按账号类型修改密码
    public void changePassword(Long accountId, String accountType, PasswordChangeRequest request) {
        if (ACCOUNT_ADMIN.equals(accountType)) {
            Admin admin = adminMapper.selectById(accountId);
            if (admin == null) {
                throw new BusinessException(404, "管理员不存在");
            }
            // 做一步原密码比对
            if (!admin.getPassword().equals(request.getOldPassword())) {
                throw new BusinessException("原密码错误");
            }
            Admin patch = new Admin();
            patch.setId(accountId);
            patch.setPassword(request.getNewPassword());

            // 更细密码落库
            adminMapper.updateById(patch);
            return;
        }

        if (ACCOUNT_MERCHANT.equals(accountType)) {
            Merchant merchant = merchantMapper.selectById(accountId);
            if (merchant == null) {
                throw new BusinessException(404, "商家不存在");
            }
            if (!merchant.getPassword().equals(request.getOldPassword())) {
                throw new BusinessException("原密码错误");
            }
            Merchant patch = new Merchant();
            patch.setId(accountId);
            patch.setPassword(request.getNewPassword());
            merchantMapper.updateById(patch);
            return;
        }

        User user = userMapper.selectById(accountId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        if (!user.getPassword().equals(request.getOldPassword())) {
            throw new BusinessException("原密码错误");
        }
        User patch = new User();
        patch.setId(accountId);
        patch.setPassword(request.getNewPassword());
        userMapper.updateById(patch);
    }

    // 充值方法，保存充值记录，（表recharge_record）并回显充值后数据（重新再查一遍个人信息，user表中有账户余额字段）
    public AccountProfile recharge(Long userId, Double amount) {
        if (amount == null || amount <= 0) {
            throw new BusinessException("充值金额必须大于0");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        double balance = user.getBalance() == null ? 0.0 : user.getBalance();
        double next = Math.round((balance + amount) * 100.0) / 100.0;


        User patch = new User();
        patch.setId(userId);
        patch.setBalance(next);

        userMapper.updateById(patch);
        rechargeRecordService.save(userId, amount, next, "余额充值");
        return toUserProfile(userMapper.selectById(userId));
    }



    public boolean isUsernameTaken(String username) {
        Long adminCount = adminMapper.selectCount(new LambdaQueryWrapper<Admin>().eq(Admin::getUsername, username));
        Long userCount = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        Long merchantCount = merchantMapper.selectCount(new LambdaQueryWrapper<Merchant>().eq(Merchant::getUsername, username));
        return adminCount > 0 || userCount > 0 || merchantCount > 0;
    }

    private LoginResponse buildAdminLoginResponse(Admin admin) {
        String token = jwtUtils.generateToken(admin.getId(), admin.getUsername(), ACCOUNT_ADMIN, ACCOUNT_ADMIN);
        return new LoginResponse(
                token,
                admin.getUsername(),
                admin.getNickname(),
                ACCOUNT_ADMIN,
                admin.getAvatar(),
                admin.getId(),
                ACCOUNT_ADMIN,
                null
        );
    }

    private LoginResponse buildUserLoginResponse(User user) {
        String token = jwtUtils.generateToken(user.getId(), user.getUsername(), ACCOUNT_USER, ACCOUNT_USER);
        return new LoginResponse(
                token,
                user.getUsername(),
                user.getNickname(),
                ACCOUNT_USER,
                user.getAvatar(),
                user.getId(),
                ACCOUNT_USER,
                null
        );
    }

    private LoginResponse buildMerchantLoginResponse(Merchant merchant) {
        String token = jwtUtils.generateToken(merchant.getId(), merchant.getUsername(), ACCOUNT_MERCHANT, ACCOUNT_MERCHANT);
        return new LoginResponse(
                token,
                merchant.getUsername(),
                merchant.getNickname(),
                ACCOUNT_MERCHANT,
                merchant.getAvatar(),
                merchant.getId(),
                ACCOUNT_MERCHANT,
                null
        );
    }

    private AccountProfile toAdminProfile(Admin admin) {
        AccountProfile profile = new AccountProfile();
        profile.setId(admin.getId());
        profile.setUsername(admin.getUsername());
        profile.setNickname(admin.getNickname());
        profile.setAvatar(admin.getAvatar());
        profile.setPhone(admin.getPhone());
        profile.setRole(ACCOUNT_ADMIN);
        profile.setAccountType(ACCOUNT_ADMIN);
        profile.setStatus(admin.getStatus());
        profile.setCreatedAt(admin.getCreatedAt());
        return profile;
    }

    private AccountProfile toUserProfile(User user) {
        AccountProfile profile = new AccountProfile();
        profile.setId(user.getId());
        profile.setUsername(user.getUsername());
        profile.setNickname(user.getNickname());
        profile.setAvatar(user.getAvatar());
        profile.setPhone(user.getPhone());
        profile.setRole(ACCOUNT_USER);
        profile.setAccountType(ACCOUNT_USER);
        profile.setStatus(user.getStatus());
        profile.setBalance(user.getBalance());
        profile.setCreatedAt(user.getCreatedAt());
        return profile;
    }

    private AccountProfile toMerchantProfile(Merchant merchant) {
        AccountProfile profile = new AccountProfile();
        profile.setId(merchant.getId());
        profile.setUsername(merchant.getUsername());
        profile.setNickname(merchant.getNickname());
        profile.setAvatar(merchant.getAvatar());
        profile.setPhone(merchant.getPhone());
        profile.setRole(ACCOUNT_MERCHANT);
        profile.setAccountType(ACCOUNT_MERCHANT);
        profile.setStatus(merchant.getStatus());
        profile.setAuditStatus(merchant.getAuditStatus());
        profile.setShopName(merchant.getShopName());
        profile.setShopDesc(merchant.getShopDesc());
        profile.setAuditRemark(merchant.getAuditRemark());
        profile.setCreatedAt(merchant.getCreatedAt());
        return profile;
    }
}
