package com.szwego.gallery.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class OrderVoucherDetailVO {
    private Long id;
    private String voucherNo;
    private String publicCode;
    private String status;
    private String paymentStatus;
    private String customerName;
    private String customerContactType;
    private String customerContactValue;
    private Long shippingAddressId;
    private String shippingAddressSnapshot;
    private String currencyCode;
    private Integer itemCount;
    private BigDecimal subtotalAmount;
    private BigDecimal shippingFee;
    private BigDecimal discountAmount;
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private BigDecimal balanceAmount;
    private String remark;
    private String internalNote;
    private Integer shareCount;
    private Integer viewCount;
    private LocalDateTime expireTime;
    private LocalDateTime lastSharedTime;
    private LocalDateTime lastViewTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Boolean expired;
    private String voucherUrl;
    private String posterUrl;
    private List<OrderVoucherPaymentSelectionVO> paymentMethods;
    private List<OrderVoucherItemVO> items;
}
