package com.szwego.gallery.dto;

import lombok.Data;

import java.util.List;

@Data
public class OrderVoucherPaymentSelectionVO {
    private Long methodId;
    private String name;
    private String type;
    private String description;
    private String accountValue;
    private String payUrl;
    private List<OrderVoucherBankFieldDTO> bankFields;
}
