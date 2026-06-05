package com.szwego.gallery.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OssConfigVO {
    private Long id;
    private Integer enabled;
    private String endpoint;
    private String bucketName;
    private String accessKeyId;
    private String accessKeySecretMasked;
    private String bucketDomain;
    private LocalDateTime lastTestTime;
    private Integer lastTestStatus;
    private String lastTestMessage;
}
