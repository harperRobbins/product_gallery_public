package com.szwego.gallery.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ProductCardVO {
    private Long id;
    private String title;
    private String enTitle;
    private String sku;
    private BigDecimal price;
    private String coverImage;
    private String videoUrl;
    private Integer imageCount;
    private Integer status;
    private Integer isTop;
    private String categoryName;
    private List<String> tags;
    private List<String> zhTags;
    private List<String> enTags;
    private List<String> imageUrls;
    private Long sourceTimestamp;
    private LocalDateTime updateTime;
    private LocalDateTime createTime;
}
