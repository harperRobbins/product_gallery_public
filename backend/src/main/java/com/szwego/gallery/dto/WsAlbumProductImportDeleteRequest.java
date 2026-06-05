package com.szwego.gallery.dto;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import java.util.List;

@Data
public class WsAlbumProductImportDeleteRequest {
    @NotEmpty(message = "请选择商品")
    private List<Long> ids;
}
