package com.livehouse.util;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 演出项目「活动介绍」长图：存储与目录规范
 */
public final class PerformanceDetailImages {

    public static final int MAX_COUNT = 10;
    private static final Pattern FOLDER_PATTERN = Pattern.compile("^show/detail/show_\\d+$");
    private static final String DRAFT_FOLDER = "show/detail/draft";

    private PerformanceDetailImages() {
    }

    public static String folderFor(Long performanceId) {
        return "show/detail/show_" + performanceId;
    }

    public static String draftFolder() {
        return DRAFT_FOLDER;
    }

    public static boolean isAllowedUploadFolder(String folder) {
        if (folder == null) {
            return false;
        }
        String f = folder.trim().toLowerCase();
        return FOLDER_PATTERN.matcher(f).matches() || DRAFT_FOLDER.equals(f);
    }

    public static List<String> parseList(String json) {
        if (StrUtil.isBlank(json)) {
            return new ArrayList<>();
        }
        String trimmed = json.trim();
        if (!trimmed.startsWith("[")) {
            return new ArrayList<>();
        }
        JSONArray arr = JSONUtil.parseArray(trimmed);
        List<String> out = new ArrayList<>();
        for (Object o : arr) {
            if (o == null) {
                continue;
            }
            String s = String.valueOf(o).trim();
            if (!s.isEmpty()) {
                out.add(s);
            }
            if (out.size() >= MAX_COUNT) {
                break;
            }
        }
        return out;
    }

    public static String toJson(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return "[]";
        }
        List<String> limited = urls.size() > MAX_COUNT ? urls.subList(0, MAX_COUNT) : urls;
        return JSONUtil.toJsonStr(limited);
    }

    public static Object coerceToUrlList(Object raw) {
        if (raw == null) {
            return new ArrayList<String>();
        }
        if (raw instanceof List) {
            return raw;
        }
        if (raw instanceof String) {
            return parseList((String) raw);
        }
        return new ArrayList<String>();
    }

    /**
     * 将图片归拢到 uploads/show/detail/show_{id}/ 并返回规范后的 URL 列表
     */
    public static List<String> normalizeUrls(Long performanceId, List<String> urls, Path uploadRoot, String urlPrefix)
            throws IOException {
        if (performanceId == null) {
            throw new IllegalArgumentException("performanceId 不能为空");
        }
        if (urls == null) {
            return new ArrayList<>();
        }
        if (urls.size() > MAX_COUNT) {
            throw new IllegalArgumentException("详情长图最多 " + MAX_COUNT + " 张");
        }
        String targetFolder = folderFor(performanceId);
        Path targetDir = uploadRoot.resolve(targetFolder).normalize();
        Files.createDirectories(targetDir);

        String prefix = urlPrefix == null ? "/uploads/" : urlPrefix;
        if (!prefix.endsWith("/")) {
            prefix = prefix + "/";
        }
        String targetPrefix = prefix + targetFolder + "/";

        List<String> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String url : urls) {
            if (StrUtil.isBlank(url)) {
                continue;
            }
            String u = url.trim();
            String relative = toRelativePath(u, prefix);
            if (relative == null) {
                continue;
            }
            Path source = uploadRoot.resolve(relative).normalize();
            if (!source.startsWith(uploadRoot) || !Files.exists(source)) {
                if (u.startsWith(prefix) || u.startsWith("/uploads/")) {
                    out.add(u);
                }
                continue;
            }
            String filename = source.getFileName().toString();
            Path dest = targetDir.resolve(filename);
            if (!source.equals(dest)) {
                Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
            }
            String canonical = targetPrefix + filename;
            if (seen.add(canonical)) {
                out.add(canonical);
            }
        }
        return out;
    }

    private static String toRelativePath(String url, String urlPrefix) {
        String u = url;
        if (u.startsWith("http://") || u.startsWith("https://")) {
            int idx = u.indexOf("/uploads/");
            if (idx < 0) {
                return null;
            }
            u = u.substring(idx + "/uploads/".length());
            return u;
        }
        if (u.startsWith(urlPrefix)) {
            u = u.substring(urlPrefix.length());
        } else if (u.startsWith("/uploads/")) {
            u = u.substring("/uploads/".length());
        } else if (u.startsWith("/")) {
            return null;
        }
        return u.replace("\\", "/");
    }
}