package com.szwego.gallery.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product_image")
public class ProductImage extends BaseEntity {
    @TableId
    private Long id;
    private Long productId;
    private String imageUrl;
    private Long imageSizeKb;
    private Integer sort;
}
