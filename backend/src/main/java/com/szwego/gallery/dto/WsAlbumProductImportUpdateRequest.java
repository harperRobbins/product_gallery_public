package com.szwego.gallery.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class WsAlbumProductImportUpdateRequest {
    @NotNull(message = "id不能为空")
    private Long id;
    private String title;
    private String subTitle;
    private String itemPrice;
    private String currencyCode;
    private String tagGroupId;
    private String abnormalReason;
}
