package com.szwego.gallery.config;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
@RequiredArgsConstructor
public class LlmTableInitializer {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void init() {
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS llm_config ("
                        + "id BIGINT NOT NULL,"
                        + "enabled TINYINT NOT NULL DEFAULT 0,"
                        + "provider VARCHAR(32) NOT NULL DEFAULT 'openai-compatible',"
                        + "base_url VARCHAR(255) DEFAULT NULL,"
                        + "api_key_enc VARCHAR(2048) DEFAULT NULL,"
                        + "model VARCHAR(128) DEFAULT NULL,"
                        + "temperature DECIMAL(4,2) DEFAULT 0.30,"
                        + "max_tokens INT DEFAULT 800,"
                        + "target_lang VARCHAR(16) DEFAULT 'en',"
                        + "strict_mode TINYINT NOT NULL DEFAULT 0,"
                        + "system_prompt TEXT,"
                        + "user_prompt_template TEXT,"
                        + "last_test_time DATETIME DEFAULT NULL,"
                        + "last_test_status TINYINT DEFAULT NULL,"
                        + "last_test_message VARCHAR(512) DEFAULT NULL,"
                        + "create_time DATETIME DEFAULT CURRENT_TIMESTAMP,"
                        + "update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                        + "PRIMARY KEY (id),"
                        + "KEY idx_enabled (enabled)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
        );
    }
}

