package com.szwego.gallery.dto;

import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Min;
import java.math.BigDecimal;

@Data
public class OrderVoucherItemRequest {
    private Long productId;
    private String title;
    private String sku;
    private String imageUrl;

    @DecimalMin(value = "0.0", inclusive = true, message = "单价不能为负")
    private BigDecimal unitPrice;

    @Min(value = 1, message = "订购数量至少为1")
    private Integer quantity;

    private String remark;
    private Integer sort;
}
