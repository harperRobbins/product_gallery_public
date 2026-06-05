package com.szwego.gallery.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AnalyticsOverviewVO {

    private long pv;
    private long uv;
    private long avgStaySeconds;
    private List<AnalyticsDailyTrendVO> trend = new ArrayList<AnalyticsDailyTrendVO>();
    private List<AnalyticsTopPageVO> topPages = new ArrayList<AnalyticsTopPageVO>();
}
