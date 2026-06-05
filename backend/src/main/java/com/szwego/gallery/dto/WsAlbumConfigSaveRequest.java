package com.szwego.gallery.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;

@Data
public class WsAlbumConfigSaveRequest {
    private Long id;
    private String configName;

    @NotBlank(message = "token不能为空")
    private String token;

    @Min(value = 0, message = "enabled只能是0或1")
    @Max(value = 1, message = "enabled只能是0或1")
    private Integer enabled = 1;

    private String defaultTransLang = "en";
    private String defaultXWgLang = "zh";
    private Integer defaultMaxPages = 20;
    private String remark;
}
