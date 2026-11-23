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
import com.livehouse.util.UserGender;
import com.livehouse.vo.app.user.UserCenterVo;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

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
    public UserCenterVo center(Long userId) {
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

        UserCenterVo data = new UserCenterVo();
        data.setUserId(String.valueOf(userId));
        data.setNickName(user == null ? "" : user.getNickName());
        data.setIcon(user == null ? null : user.getIcon());
        data.setPhone(maskPhone(user == null ? null : user.getPhone()));
        data.setCity(info == null ? "" : info.getCity());
        data.setSignature(info == null ? "" : info.getIntroduce());
        data.setGender(info == null || info.getGender() == null ? 0 : info.getGender());
        data.setGenderText(UserGender.text(info));
        data.setBirthday(info == null || info.getBirthday() == null ? null : info.getBirthday().toString());
        data.setWatchCount(info == null || info.getWatchCount() == null ? 0 : info.getWatchCount());
        data.setFavoriteCount(favCount);
        data.setMessageUnread((long) appMessageService.countUnread(userId));
        data.setCredits(creditAccountService.sumAvailableCredits(userId));
        data.setTotalCredits(totalCredits);
        data.setLevel(level.getLevel());
        data.setLevelName(level.getName());
        data.setDiscountRate(level.getDiscountRate());
        if (next != null) {
            data.setNextLevelName(next.getName() + " Lv." + next.getLevel());
            data.setNextLevelNeed(Math.max(0, next.getMinTotalCredits() - totalCredits));
        } else {
            data.setNextLevelName(null);
            data.setNextLevelNeed(0);
        }
        data.setAuthStatus(authStatus);
        data.setAuthStatusText(authStatusText);
        return data;
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

}