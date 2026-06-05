package com.szwego.gallery.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.currency")
public class CurrencyRateProperties {
    private String baseCurrency = "CNY";
    private String sourceName = "frankfurter";
    private String apiUrl = "https://api.frankfurter.dev/v2/rates";
    private Integer timeoutMs = 10000;
    private String refreshCron = "0 10 3 * * *";
    private String zone = "Asia/Shanghai";
    private List<String> supportedCurrencies = Arrays.asList("USD", "EUR", "GBP", "JPY", "CNY");
}
