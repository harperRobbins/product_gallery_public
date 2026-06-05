package com.szwego.gallery.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.llm")
public class LlmProperties {
    private String keySecret = "change_this_llm_secret";
    private Integer requestTimeoutMs = 60000;
}
