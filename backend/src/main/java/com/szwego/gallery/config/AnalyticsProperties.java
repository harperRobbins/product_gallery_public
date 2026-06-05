package com.szwego.gallery.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "gallery.analytics")
public class AnalyticsProperties {

    private boolean enabled = true;
    private String ipSalt = "change_this_analytics_salt";
}
