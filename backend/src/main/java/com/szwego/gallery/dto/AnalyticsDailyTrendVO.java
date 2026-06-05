package com.szwego.gallery.dto;

import lombok.Data;

@Data
public class AnalyticsDailyTrendVO {

    private String statDate;
    private long pv;
    private long uv;
    private long avgStaySeconds;
}
