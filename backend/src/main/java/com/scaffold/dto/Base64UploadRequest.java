package com.scaffold.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Base64 上传请求")
public class Base64UploadRequest {

    @Schema(description = "Base64 编码的文件数据", example = "data:image/jpeg;base64,/9j/4AAQSkZJRg...")
    private String data;

    @Schema(description = "文件扩展名,默认 jpg", example = "jpg")
    private String extension = "jpg";
}
