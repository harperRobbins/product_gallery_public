package com.szwego.gallery.dto;

import lombok.Data;

@Data
public class DashboardStatVO {
    private Long totalProducts;
    private Long totalCategories;
    private Long totalImages;
    private Long publishedToday;
    private Double imageStorageMb;
}
