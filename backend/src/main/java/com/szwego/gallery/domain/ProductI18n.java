package com.szwego.gallery.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product_i18n")
public class ProductI18n extends BaseEntity {

    @TableId
    private Long id;

    private Long productId;
    private String langCode;
    private String title;
    private String description;
}

