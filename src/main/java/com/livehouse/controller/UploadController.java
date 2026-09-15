package com.livehouse.controller;

import com.livehouse.dto.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 图片上传控制器
 */
@RestController
@RequestMapping("/upload")
public class UploadController {

    private static final Set<String> ALLOWED_EXTENSIONS =
            new HashSet<>(Arrays.asList("jpg", "jpeg", "png", "gif", "webp"));

    @Value("${livehouse.upload.dir:frontend-livehouse/uploads}")
    private String uploadDir;

    @Value("${livehouse.upload.url-prefix:/uploads/}")
    private String urlPrefix;

    @PostMapping("/image")
    public Result uploadImage(@RequestParam("file") MultipartFile file,
                              @RequestParam(value = "folder", required = false) String folder) throws IOException {
        if (file == null || file.isEmpty()) {
            return Result.fail("请选择要上传的图片");
        }

        String originalFilename = file.getOriginalFilename();
        String extension = getExtension(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            return Result.fail("仅支持 jpg、jpeg、png、gif、webp 格式图片");
        }

        String filename = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        String safeFolder = getSafeFolder(folder);
        Path dir = resolveUploadDir().resolve(safeFolder).normalize();
        Files.createDirectories(dir);
        Files.copy(file.getInputStream(), dir.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
        return Result.ok(urlPrefix + (safeFolder.isEmpty() ? "" : safeFolder + "/") + filename);
    }

    private Path resolveUploadDir() {
        Path path = Paths.get(uploadDir);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        return Paths.get(System.getProperty("user.dir")).resolve(path).normalize();
    }

    private String getSafeFolder(String folder) {
        if (folder == null || folder.trim().isEmpty()) {
            return "";
        }
        String value = folder.trim().toLowerCase();
        if ("avatars".equals(value) || "shows".equals(value)) {
            return value;
        }
        return "";
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}
