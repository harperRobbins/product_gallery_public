package com.szwego.gallery.dto;

import lombok.Data;

@Data
public class OssConfigSaveRequest {
    private Long id;
    private Integer enabled;
    private String endpoint;
    private String bucketName;
    private String accessKeyId;
    private String accessKeySecret;
    private String bucketDomain;
}
