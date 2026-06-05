package com.szwego.gallery.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("llm_config")
public class LlmConfig extends BaseEntity {

    @TableId
    private Long id;

    private Integer enabled;
    private String provider;
    private String baseUrl;
    private String apiKeyEnc;
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

