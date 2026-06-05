package com.szwego.gallery.dto;

import lombok.Data;

@Data
public class ShopMenuItemDTO {
    private String id;
    private String parentId;
    private String name;
    private String targetType;
    private Long categoryId;
    private String tagName;
    private String customPageKey;
    private Integer sort;
    private Integer enabled;
}
