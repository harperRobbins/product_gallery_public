package com.szwego.gallery.dto;

import lombok.Data;

@Data
public class ShopContactItemDTO {
    private String id;
    private String type;
    private String label;
    private String value;
    private String copyValue;
    private Integer sort;
    private Integer enabled;
}
