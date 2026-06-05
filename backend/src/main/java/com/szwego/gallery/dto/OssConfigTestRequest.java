package com.szwego.gallery.dto;

import lombok.Data;

@Data
public class OssConfigTestRequest {
    private String endpoint;
    private String bucketName;
    private String accessKeyId;
    private String accessKeySecret;
}
