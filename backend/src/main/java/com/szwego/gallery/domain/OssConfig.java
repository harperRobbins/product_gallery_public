package com.szwego.gallery.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oss_config")
public class OssConfig extends BaseEntity {
    @TableId
    private Long id;
    private Integer enabled;
    private String endpoint;
    private String bucketName;
    private String accessKeyId;
    private String accessKeySecret;
    private String bucketDomain;
    private LocalDateTime lastTestTime;
    private Integer lastTestStatus;
    private String lastTestMessage;
}

