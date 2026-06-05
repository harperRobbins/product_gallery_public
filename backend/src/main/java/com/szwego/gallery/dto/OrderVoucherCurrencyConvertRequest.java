package com.szwego.gallery.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class OrderVoucherCurrencyConvertRequest {
    @NotBlank(message = "目标币种不能为空")
    private String targetCurrency;
}
