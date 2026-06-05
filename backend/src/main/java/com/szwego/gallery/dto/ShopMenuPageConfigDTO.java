package com.szwego.gallery.dto;

import lombok.Data;

import java.util.List;

@Data
public class ShopMenuPageConfigDTO {
    private String menuId;
    private Integer enabled;
    private List<ShopMenuPageBlockDTO> blocks;
}
