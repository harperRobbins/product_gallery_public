package com.szwego.gallery.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("order_voucher")
public class OrderVoucher extends BaseEntity {
    @TableId
    private Long id;
    private String voucherNo;
    private String publicCode;
    private String status;
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
    private String paymentStatus;
    private String paymentMethodsJson;
    private String remark;
    private String internalNote;
    private LocalDateTime expireTime;
    private Integer shareCount;
    private Integer viewCount;
    private LocalDateTime lastSharedTime;
    private LocalDateTime lastViewTime;
}
