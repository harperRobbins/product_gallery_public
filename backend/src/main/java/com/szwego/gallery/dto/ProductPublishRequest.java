package com.szwego.gallery.dto;

import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

@Data
public class ProductPublishRequest {
    private Long id;

    @NotBlank(message = "标题不能为空")
    private String title;

    private String enTitle;

    @NotBlank(message = "详细描述不能为空")
    private String description;

    private String enDescription;

    @NotNull(message = "分类不能为空")
    private Long categoryId;

    @NotNull(message = "价格不能为空")
    @DecimalMin(value = "0.0", inclusive = true, message = "价格不能为负")
    private BigDecimal price;

    @NotBlank(message = "货号不能为空")
    private String sku;

    private List<String> tags;

    @NotEmpty(message = "至少上传1张商品图")
    private List<String> imageUrls;

    private String videoUrl;

    private List<Long> imageSizesKb;

    private Integer status;

    private Integer isTop;
}
