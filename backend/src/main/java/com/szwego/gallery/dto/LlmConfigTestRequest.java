package com.szwego.gallery.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class LlmConfigTestRequest {
    private String provider;
    private String baseUrl;
    private String apiKey;
    private String model;
    private BigDecimal temperature;
    private Integer maxTokens;
    private String targetLang;
    private String systemPrompt;
    private String userPromptTemplate;
    private String title;
    private String description;
}
