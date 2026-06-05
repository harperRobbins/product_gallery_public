package com.szwego.gallery.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProductDetailVO extends ProductCardVO {
    private String description;
    private String enDescription;
    private Long categoryId;
}
