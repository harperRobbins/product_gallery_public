package com.szwego.gallery.controller;

import com.szwego.gallery.common.ApiResponse;
import com.szwego.gallery.dto.CurrencyRateSnapshotVO;
import com.szwego.gallery.service.CurrencyRateService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CurrencyRateController {

    private final CurrencyRateService currencyRateService;

    @GetMapping("/api/currency/rates")
    public ApiResponse<CurrencyRateSnapshotVO> latestRates() {
        return ApiResponse.success(currencyRateService.getLatestSnapshot());
    }
}
