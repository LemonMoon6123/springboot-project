package com.livehouse.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 用户资料 DTO
 */
@Data
public class UserProfileDTO {
    private Long id;
    private String phone;
    private String password;
    private String nickName;
    private String icon;
    private String city;
    private String introduce;
    private Integer gender;
    private LocalDate birthday;
}
