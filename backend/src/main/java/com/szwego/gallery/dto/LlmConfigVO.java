package com.szwego.gallery.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class LlmConfigVO {
    private Long id;
    private Integer enabled;
    private String provider;
    private String baseUrl;
    private String apiKeyMasked;
    private String model;
    private BigDecimal temperature;
    private Integer maxTokens;
    private String targetLang;
    private Integer strictMode;
    private String systemPrompt;
    private String userPromptTemplate;
    private LocalDateTime lastTestTime;
    private Integer lastTestStatus;
    private String lastTestMessage;
}

