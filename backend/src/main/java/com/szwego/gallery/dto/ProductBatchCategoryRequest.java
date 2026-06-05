package com.szwego.gallery.dto;

import lombok.Data;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

@Data
public class ProductBatchCategoryRequest {
    @NotEmpty(message = "请至少选择一个商品")
    private List<Long> productIds;

    @NotNull(message = "请选择目标分类")
    private Long categoryId;
}
