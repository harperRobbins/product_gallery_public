package com.szwego.gallery.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("currency_rate")
public class CurrencyRate extends BaseEntity {
    @TableId
    private Long id;
    private String baseCurrency;
    private String targetCurrency;
    private BigDecimal rate;
    private LocalDate rateDate;
    private String source;
}
