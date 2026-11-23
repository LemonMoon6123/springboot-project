package com.livehouse.controller;

import com.livehouse.entity.Result;
import com.livehouse.entity.StatusCode;
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
import com.livehouse.util.PerformanceDetailImages;

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
            return Result.fail(StatusCode.FILE_EMPTY);
        }

        String originalFilename = file.getOriginalFilename();
        String extension = getExtension(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            return Result.fail(StatusCode.FILE_TYPE_NOT_ALLOWED);
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

    private static final Set<String> ALLOWED_FOLDERS = new HashSet<>(Arrays.asList(
            "avatars", "show", "shows", "poster", "comment"
    ));

    private String getSafeFolder(String folder) {
        if (folder == null || folder.trim().isEmpty()) {
            return "";
        }
        String value = folder.trim().replace('\\', '/');
        String lower = value.toLowerCase();
        if (ALLOWED_FOLDERS.contains(lower)) {
            return lower;
        }
        if (PerformanceDetailImages.isAllowedUploadFolder(lower)) {
            return lower;
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
