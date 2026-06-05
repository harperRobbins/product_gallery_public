package com.szwego.gallery.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class OrderVoucherAdminVO {
    private Long id;
    private String voucherNo;
    private String publicCode;
    private String status;
    private String paymentStatus;
    private String customerName;
    private String customerContactType;
    private String customerContactValue;
    private String currencyCode;
    private Integer itemCount;
    private String itemSummary;
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private BigDecimal balanceAmount;
    private Integer shareCount;
    private Integer viewCount;
    private LocalDateTime expireTime;
    private LocalDateTime lastSharedTime;
    private LocalDateTime lastViewTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
