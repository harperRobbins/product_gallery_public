package com.szwego.gallery.controller;

import com.szwego.gallery.common.ApiResponse;
import com.szwego.gallery.dto.AnalyticsOverviewVO;
import com.szwego.gallery.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AnalyticsAdminController {

    private final AnalyticsService analyticsService;

    @GetMapping("/api/admin/analytics/overview")
    public ApiResponse<AnalyticsOverviewVO> overview(
            @RequestParam(value = "days", required = false) Integer days,
            @RequestParam(value = "topN", required = false) Integer topN
    ) {
        return ApiResponse.success(analyticsService.overview(days, topN));
    }
}
