package com.szwego.gallery.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotEmpty;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class OrderVoucherSaveRequest {
    private String status;
    private String customerName;
    private String customerContactType;
    private String customerContactValue;
    private Long shippingAddressId;
    private String currencyCode;

    @DecimalMin(value = "0.0", inclusive = true, message = "运费不能为负")
    private BigDecimal shippingFee;

    @DecimalMin(value = "0.0", inclusive = true, message = "优惠金额不能为负")
    private BigDecimal discountAmount;

    @DecimalMin(value = "0.0", inclusive = true, message = "已付金额不能为负")
    private BigDecimal paidAmount;

    private String remark;
    private String internalNote;
    private LocalDateTime expireTime;
    private List<OrderVoucherPaymentSelectionRequest> paymentMethods;

    @Valid
    @NotEmpty(message = "至少添加1个商品")
    private List<OrderVoucherItemRequest> items;
}
