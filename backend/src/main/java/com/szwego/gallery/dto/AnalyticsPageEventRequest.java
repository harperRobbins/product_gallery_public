package com.szwego.gallery.dto;

import lombok.Data;

@Data
public class AnalyticsPageEventRequest {

    private String pagePath;
    private String pageTitle;
    private String visitorId;
    private String sessionId;
    private Long staySeconds;
    private String referrer;
    private String lang;
    private Integer screenWidth;
    private Integer screenHeight;
    private String timezone;
    private Long occurredAt;
}
