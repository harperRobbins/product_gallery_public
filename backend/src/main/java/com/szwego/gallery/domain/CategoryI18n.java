package com.szwego.gallery.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("category_i18n")
public class CategoryI18n extends BaseEntity {

    @TableId
    private Long id;

    private Long categoryId;
    private String langCode;
    private String name;
}

