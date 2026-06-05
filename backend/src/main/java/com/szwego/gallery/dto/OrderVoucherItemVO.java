package com.szwego.gallery.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderVoucherItemVO {
    private Long id;
    private Long voucherId;
    private String sourceType;
    private Long productId;
    private String title;
    private String sku;
    private String imageUrl;
    private BigDecimal unitPrice;
    private Integer quantity;
    private BigDecimal lineAmount;
    private String remark;
    private Integer sort;
}
