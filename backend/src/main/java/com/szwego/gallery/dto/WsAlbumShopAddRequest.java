package com.szwego.gallery.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class WsAlbumShopAddRequest {
    @NotBlank(message = "albumId不能为空")
    private String albumId;
    private String remark;
}
