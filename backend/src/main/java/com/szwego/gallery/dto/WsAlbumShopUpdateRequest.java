package com.szwego.gallery.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class WsAlbumShopUpdateRequest {
    @NotBlank(message = "shopId不能为空")
    private String shopId;
    private String remark;
    private Integer status;
    private String priceDetectConfigJson;
}
