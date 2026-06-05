package com.szwego.gallery.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class OrderVoucherPublicVO {
    private String voucherNo;
    private String publicCode;
    private String status;
    private String paymentStatus;
    private String customerName;
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
    private LocalDateTime expireTime;
    private LocalDateTime createTime;
    private Boolean expired;
    private String voucherUrl;
    private String shopName;
    private String shopLogo;
    private String shopAnnouncement;
    private List<OrderVoucherPaymentSelectionVO> paymentMethods;
    private List<ShopContactItemDTO> contacts;
    private List<OrderVoucherItemVO> items;
}
