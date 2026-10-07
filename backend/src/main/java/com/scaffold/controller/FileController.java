package com.scaffold.controller;

import com.scaffold.common.Result;
import com.scaffold.service.FileUploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/files")
@Tag(name = "A03-公共-文件上传", description = "图片/文件上传,返回可访问 URL")
public class FileController {

    @Autowired
    private FileUploadService fileUploadService;

    @PostMapping("/upload")
    @Operation(summary = "上传文件", description = "multipart 上传文件,返回可访问的 URL 地址")
    public Result<Map<String, String>> upload(@Parameter(description = "上传的文件") @RequestParam("file") MultipartFile file) {
        String url = fileUploadService.upload(file);
        return Result.ok(Map.of("url", url));
    }
}
