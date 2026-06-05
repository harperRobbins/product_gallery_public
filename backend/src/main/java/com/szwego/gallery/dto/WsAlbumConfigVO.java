package com.szwego.gallery.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WsAlbumConfigVO {
    private Long id;
    private String configName;
    private String tokenMasked;
    private Integer enabled;
    private String defaultTransLang;
    private String defaultXWgLang;
    private Integer defaultMaxPages;
    private String remark;
    private LocalDateTime lastVerifyTime;
    private LocalDateTime lastSuccessTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
