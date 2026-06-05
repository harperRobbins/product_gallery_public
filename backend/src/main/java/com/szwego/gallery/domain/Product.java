package com.szwego.gallery.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product")
public class Product extends BaseEntity {
    @TableId
    private Long id;
    private String title;
    private String description;
    private Long categoryId;
    private BigDecimal price;
    private String sku;
    private String tagsJson;
    private String coverImage;
    private String videoUrl;
    private Integer imageCount;
    private Integer status;
    private Integer isTop;
    private Integer views;
}
