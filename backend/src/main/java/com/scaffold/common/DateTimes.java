package com.scaffold.common;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** 时间统一存成字符串，格式：yyyy-MM-dd HH:mm:ss */
public class DateTimes {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static String now() {
        return LocalDateTime.now().format(DATE_TIME);
    }

    public static String format(LocalDateTime time) {
        return time == null ? null : time.format(DATE_TIME);
    }

    public static LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim().replace('T', ' ');
        if (text.length() >= 19) {
            return LocalDateTime.parse(text.substring(0, 19), DATE_TIME);
        }
        if (text.length() >= 16) {
            return LocalDateTime.parse(text.substring(0, 16) + ":00", DATE_TIME);
        }
        return null;
    }

    public static String plusHours(String base, long hours) {
        LocalDateTime time = parseDateTime(base);
        if (time == null) {
            time = LocalDateTime.now();
        }
        return format(time.plusHours(hours));
    }

    public static LocalDate toDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String text = value.trim().replace('T', ' ');
        if (text.length() < 10) {
            return null;
        }
        return LocalDate.parse(text.substring(0, 10));
    }

    public static String startOfDay(String value) {
        LocalDate date = toDate(value);
        return date == null ? null : date + " 00:00:00";
    }

    public static String endOfDay(String value) {
        LocalDate date = toDate(value);
        return date == null ? null : date + " 23:59:59";
    }
}
