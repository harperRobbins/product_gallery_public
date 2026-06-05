package com.szwego.gallery.dto;

import lombok.Data;

@Data
public class AnalyticsTopPageVO {

    private String pagePath;
    private String pageTitle;
    private long pv;
    private long uv;
    private long avgStaySeconds;
}
