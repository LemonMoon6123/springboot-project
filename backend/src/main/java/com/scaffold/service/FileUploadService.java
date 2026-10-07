package com.scaffold.service;

import com.scaffold.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;

@Service
public class FileUploadService {

    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp", "svg");
    private static final Set<String> VIDEO_EXTENSIONS = Set.of("mp4", "webm", "mov", "avi", "mkv", "m4v");
    private static final Set<String> ALLOWED_EXTENSIONS;

    static {
        java.util.HashSet<String> set = new java.util.HashSet<>();
        set.addAll(IMAGE_EXTENSIONS);
        set.addAll(VIDEO_EXTENSIONS);
        ALLOWED_EXTENSIONS = Set.copyOf(set);
    }

    @Value("${app.upload.path:uploads}")
    private String uploadPath;

    public String upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = resolveExtension(originalFilename, file.getContentType());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException("仅支持图片（jpg/png/gif/webp/svg）或视频（mp4/webm/mov/avi/mkv）");
        }

        try {
            Path dir = resolveUploadDir();
            String filename = UUID.randomUUID() + "." + extension;
            Path target = dir.resolve(filename);
            // 使用 Files.copy，避免 Windows 下 MultipartFile.transferTo 路径问题
            try (var in = file.getInputStream()) {
                Files.copy(in, target);
            }
            return "/uploads/" + filename;
        } catch (IOException ex) {
            throw new BusinessException("文件上传失败：" + ex.getMessage());
        }
    }

    public String uploadBase64(String base64Data, String extension) {
        if (base64Data == null || base64Data.isBlank()) {
            throw new BusinessException("上传文件不能为空");
        }
        String ext = normalizeExtension(extension);
        try {
            String payload = base64Data;
            int commaIndex = payload.indexOf(',');
            if (commaIndex >= 0) {
                payload = payload.substring(commaIndex + 1);
            }
            byte[] bytes = Base64.getDecoder().decode(payload);
            if (bytes.length == 0) {
                throw new BusinessException("上传文件不能为空");
            }
            Path dir = resolveUploadDir();
            String filename = UUID.randomUUID() + "." + ext;
            Path target = dir.resolve(filename);
            Files.write(target, bytes);
            return "/uploads/" + filename;
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("图片数据格式不正确");
        } catch (IOException ex) {
            throw new BusinessException("文件上传失败");
        }
    }

    private Path resolveUploadDir() throws IOException {
        Path dir = Paths.get(uploadPath).toAbsolutePath().normalize();
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
        return dir;
    }

    private String normalizeExtension(String extension) {
        String ext = extension == null ? "" : extension.toLowerCase();
        if (!IMAGE_EXTENSIONS.contains(ext)) {
            return "jpg";
        }
        return ext;
    }

    private String resolveExtension(String originalFilename, String contentType) {
        if (originalFilename != null && originalFilename.contains(".")) {
            String ext = originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase();
            if (ALLOWED_EXTENSIONS.contains(ext)) {
                return ext;
            }
        }
        if (contentType != null) {
            String ct = contentType.toLowerCase();
            if (ct.contains("jpeg") || ct.contains("jpg")) {
                return "jpg";
            }
            if (ct.contains("png")) {
                return "png";
            }
            if (ct.contains("gif")) {
                return "gif";
            }
            if (ct.contains("webp")) {
                return "webp";
            }
            if (ct.contains("svg")) {
                return "svg";
            }
            if (ct.contains("mp4")) {
                return "mp4";
            }
            if (ct.contains("webm")) {
                return "webm";
            }
            if (ct.contains("quicktime")) {
                return "mov";
            }
            if (ct.contains("x-msvideo") || ct.contains("avi")) {
                return "avi";
            }
            if (ct.contains("matroska") || ct.contains("x-matroska")) {
                return "mkv";
            }
        }
        return "jpg";
    }
}
