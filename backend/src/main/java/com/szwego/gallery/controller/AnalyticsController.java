package com.szwego.gallery.controller;

import com.szwego.gallery.common.ApiResponse;
import com.szwego.gallery.dto.AnalyticsPageEventRequest;
import com.szwego.gallery.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @PostMapping("/api/analytics/page-view")
    public ApiResponse<Boolean> pageView(@RequestBody(required = false) AnalyticsPageEventRequest request, HttpServletRequest httpServletRequest) {
        analyticsService.trackPageView(request, httpServletRequest);
        return ApiResponse.success(Boolean.TRUE);
    }

    @PostMapping("/api/analytics/page-leave")
    public ApiResponse<Boolean> pageLeave(@RequestBody(required = false) AnalyticsPageEventRequest request, HttpServletRequest httpServletRequest) {
        analyticsService.trackPageLeave(request, httpServletRequest);
        return ApiResponse.success(Boolean.TRUE);
    }
}
