package com.szwego.gallery.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ws_album_config")
public class WsAlbumConfig extends BaseEntity {
    @TableId
    private Long id;
    private String configName;
    private String tokenEnc;
    private Integer enabled;
    private String defaultTransLang;
    private String defaultXWgLang;
    private Integer defaultMaxPages;
    private String remark;
    private LocalDateTime lastVerifyTime;
    private LocalDateTime lastSuccessTime;
}
