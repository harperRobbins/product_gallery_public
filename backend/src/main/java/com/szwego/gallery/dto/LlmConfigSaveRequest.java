package com.szwego.gallery.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class LlmConfigSaveRequest {
    private Long id;
    private Integer enabled;
    private String provider;
    private String baseUrl;
    private String apiKey;
    private String model;
    private BigDecimal temperature;
    private Integer maxTokens;
    private String targetLang;
    private Integer strictMode;
    private String systemPrompt;
    private String userPromptTemplate;
}

