package com.szwego.gallery.dto;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import java.util.List;

@Data
public class WsAlbumProductImportMarkAbnormalRequest {
    @NotEmpty(message = "请选择商品")
    private List<Long> ids;
    private Integer isAbnormal = 1;
    private String abnormalReason;
}
