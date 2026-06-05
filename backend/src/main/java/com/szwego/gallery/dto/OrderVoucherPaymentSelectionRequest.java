package com.szwego.gallery.dto;

import lombok.Data;

@Data
public class OrderVoucherPaymentSelectionRequest {
    private Long methodId;
    private String payUrl;
}
