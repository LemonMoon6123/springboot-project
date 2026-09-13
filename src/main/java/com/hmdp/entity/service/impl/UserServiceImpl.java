package com.hmdp.entity.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.entity.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    public Result sendCode(String phone) {
        // 1.校验手机号合法性
        if(!RegexUtils.isPhoneInvalid(phone)){
            // 1.1 手机号合法
            // 2.生成验证码
            String code = RandomUtil.randomNumbers(6);
            // 3.保存验证码到Redis中，并设置有效期，防止redis内存爆满。
            redisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone,code,LOGIN_CODE_TTL,TimeUnit.MINUTES);
            // 4.发送验证码 TODO 以后接入第三方API，实现真正短信验证码发送。
            log.info("发送短信验证码成功，验证码：{}",code);
            // 5.返回结果
            return Result.ok();
        }
        // 1.2 手机号非法，返回结果
        return Result.fail("手机号格式错误，请重新输入！");
    }

    public Result login(LoginFormDTO loginForm) {
        // 1.校验手机号合法性
        String phone = loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号格式错误，请重新输入！");
        }
        // 2.校验验证码是否一致(从redis中依照phoneKey拿到验证码)
        String code = loginForm.getCode();
        String saveCode = redisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        if(code == null || !(saveCode.equals(code))){
            return Result.fail("验证码错误，请重新输入！");
        }
        // 3.根据手机号查询用户 select * from tb_user where phone = ?
        User user = query().eq("phone", phone).one();
        // 4.保存用户信息
        if(user == null){
            user = createUserWithPhone(phone);
        }
        // 5.保存用户信息到redis数据库中，key依旧唯一（UUID）,视为token令牌，作以后的身份校验。
        String token = UUID.randomUUID().toString(true); // 生成的token字符串中不包含中横线“-”,32字符十六进制小写字符串
        String key = LOGIN_USER_KEY + token;
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        // 6.存储的用户信息数据结构选择hash结构，占用内存空间小，且能CRUD字段。
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName,fieldValue) -> fieldValue.toString()));
        redisTemplate.opsForHash().putAll(key,userMap); // key 和 value 只能是string。
        // 7.设置用户信息过期时间，同样防止redis空间爆满。（过30min后自动销毁，所以之后要做过期时间刷新）
        redisTemplate.expire(key,LOGIN_USER_TTL, TimeUnit.MINUTES);
        // 8.返回token
        return Result.ok(token);
    }

    public Result sign() {
        // 1. 获取当前登录用户信息
        Long userId = UserHolder.getUser().getId();
        // 2. 获取当前日期
        LocalDateTime now = LocalDateTime.now();
        // 3.拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern("yyyyMM"));
        String key = USER_SIGN_KEY + userId + ":" + keySuffix;
        // 4.获取当前月份
        int dayOfMonth = now.getDayOfMonth();
        // 5.向redis中存bitMap（是String结构，但string底层存储也是字节形式存储，所以最终还是位bit）
        redisTemplate.opsForValue().setBit(key,dayOfMonth - 1,true);
        return Result.ok();
    }

    public Result signCount() {
        // 1. 获取当前登录用户信息
        Long userId = UserHolder.getUser().getId();
        // 2. 获取当前日期
        LocalDateTime now = LocalDateTime.now();
        // 3.拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern("yyyyMM"));
        String key = USER_SIGN_KEY + userId + ":" + keySuffix;
        // 4.获取当前月份
        int dayOfMonth = now.getDayOfMonth();

        // 5.获取本月截止今天所有的签到记录，返回的是十进制数字 BITFIELD　key GET udayOfMonth(查几位，无符号位数) 0
        List<Long> list = redisTemplate.opsForValue().bitField(key, BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0));
        // 6.循环遍历
        Long num = list.get(0);
        if(num == null || num == 0){
            // 如果没查到信息，代表用户当月没签到，返回0
            return Result.ok(0);
        }
        int count = 0;
        while(true) {
            // 6.1 让该数和1与运算 是1返回1，是0返回0
            if ((num & 1) == 0) {
                // 如果是0，代表今天用户没签到，结束循环。
                break;
            } else {
                // 如果是1，代表用户今天签到了，计数。
                count++;
            }
            num >>>= 1; // 左移一位。
        }
        return Result.ok(count);
    }

    private User createUserWithPhone(String phone) {
        // 1.创建user对象
        User user = new User();
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        user.setPhone(phone);
        // 2.在数据库中新增该用户对象
        save(user);
        return user;
    }
}
