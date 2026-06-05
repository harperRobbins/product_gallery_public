package com.szwego.gallery.dto;

import lombok.Data;

import java.util.List;

@Data
public class OrderVoucherPaymentMethodSaveRequest {
    private String name;
    private String type;
    private String description;
    private String accountValue;
    private List<OrderVoucherBankFieldDTO> bankFields;
    private Integer enabled;
    private Integer sort;
}
