package com.livehouse.util;

import com.livehouse.entity.UserInfo;

/** 用户性别：0 未设置，1 男，2 女 */
public final class UserGender {

    private UserGender() {
    }

    public static String text(Integer gender) {
        if (gender == null || gender == 0) {
            return "未设置";
        }
        if (gender == 2) {
            return "女";
        }
        return "男";
    }

    public static String text(UserInfo info) {
        return info == null ? "未设置" : text(info.getGender());
    }
}