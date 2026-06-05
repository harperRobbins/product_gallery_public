package com.szwego.gallery.service;

import com.szwego.gallery.dto.AnalyticsOverviewVO;
import com.szwego.gallery.dto.AnalyticsPageEventRequest;

import javax.servlet.http.HttpServletRequest;

public interface AnalyticsService {

    void trackPageView(AnalyticsPageEventRequest request, HttpServletRequest httpServletRequest);

    void trackPageLeave(AnalyticsPageEventRequest request, HttpServletRequest httpServletRequest);

    AnalyticsOverviewVO overview(Integer days, Integer topN);
}
