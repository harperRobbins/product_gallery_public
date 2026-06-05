package com.szwego.gallery.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class CurrencyRateSnapshotVO {
    private String baseCurrency;
    private LocalDate rateDate;
    private LocalDateTime updatedAt;
    private List<String> supportedCurrencies;
    private Map<String, BigDecimal> rates;
}
