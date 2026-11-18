package com.livehouse.service.app.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.livehouse.entity.Favorite;
import com.livehouse.entity.MemberLevel;
import com.livehouse.entity.RealNameAuth;
import com.livehouse.entity.User;
import com.livehouse.entity.UserInfo;
import com.livehouse.mapper.FavoriteMapper;
import com.livehouse.mapper.RealNameAuthMapper;
import com.livehouse.mapper.UserInfoMapper;
import com.livehouse.service.IUserService;
import com.livehouse.service.app.AppMessageService;
import com.livehouse.service.app.AppUserCenterService;
import com.livehouse.service.app.credit.CreditAccountService;
import com.livehouse.service.app.credit.MemberLevelService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AppUserCenterServiceImpl implements AppUserCenterService {

    @Resource
    private IUserService userService;
    @Resource
    private UserInfoMapper userInfoMapper;
    @Resource
    private FavoriteMapper favoriteMapper;
    @Resource
    private RealNameAuthMapper realNameAuthMapper;
    @Resource
    private MemberLevelService memberLevelService;
    @Resource
    private CreditAccountService creditAccountService;
    @Resource
    private AppMessageService appMessageService;

    @Override
    public Map<String, Object> center(Long userId) {
        User user = userService.getById(userId);
        UserInfo info = userInfoMapper.selectById(userId);
        MemberLevel level = memberLevelService.resolveMemberLevel(userId);
        MemberLevel next = memberLevelService.nextLevel(level);
        int totalCredits = memberLevelService.sumTotalCredits(userId);

        RealNameAuth auth = realNameAuthMapper.selectOne(new LambdaQueryWrapper<RealNameAuth>()
                .eq(RealNameAuth::getUserId, userId)
                .last("LIMIT 1"));
        int authStatus = auth == null ? 0 : (auth.getAuthStatus() == null ? 0 : auth.getAuthStatus());
        String authStatusText = authStatus == RealNameAuth.STATUS_PASS ? "已认证"
                : authStatus == RealNameAuth.STATUS_AUDITING ? "审核中" : "未认证";

        long favCount = favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", String.valueOf(userId));
        data.put("nickName", user == null ? "" : user.getNickName());
        data.put("icon", user == null ? null : user.getIcon());
        data.put("phone", maskPhone(user == null ? null : user.getPhone()));
        data.put("city", info == null ? "" : info.getCity());
        data.put("signature", info == null ? "" : info.getIntroduce());
        data.put("genderText", genderText(info));
        data.put("watchCount", info == null || info.getWatchCount() == null ? 0 : info.getWatchCount());
        data.put("favoriteCount", favCount);
        data.put("messageUnread", appMessageService.countUnread(userId));
        data.put("credits", creditAccountService.sumAvailableCredits(userId));
        data.put("totalCredits", totalCredits);
        data.put("level", level.getLevel());
        data.put("levelName", level.getName());
        data.put("discountRate", level.getDiscountRate());
        if (next != null) {
            data.put("nextLevelName", next.getName() + " Lv." + next.getLevel());
            data.put("nextLevelNeed", Math.max(0, next.getMinTotalCredits() - totalCredits));
        } else {
            data.put("nextLevelName", null);
            data.put("nextLevelNeed", 0);
        }
        data.put("authStatus", authStatus);
        data.put("authStatusText", authStatusText);
        return data;
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private static String genderText(UserInfo info) {
        if (info == null || info.getGender() == null) {
            return "";
        }
        return info.getGender() == 1 ? "女" : "男";
    }
}