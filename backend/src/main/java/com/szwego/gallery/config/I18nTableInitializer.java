package com.szwego.gallery.config;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
@RequiredArgsConstructor
public class I18nTableInitializer {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void init() {
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS product_i18n ("
                        + "id BIGINT NOT NULL,"
                        + "product_id BIGINT NOT NULL,"
                        + "lang_code VARCHAR(16) NOT NULL,"
                        + "title VARCHAR(255) DEFAULT NULL,"
                        + "description TEXT DEFAULT NULL,"
                        + "create_time DATETIME DEFAULT CURRENT_TIMESTAMP,"
                        + "update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                        + "PRIMARY KEY (id),"
                        + "UNIQUE KEY uk_product_lang (product_id, lang_code),"
                        + "KEY idx_lang_title (lang_code, title)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
        );

        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS category_i18n ("
                        + "id BIGINT NOT NULL,"
                        + "category_id BIGINT NOT NULL,"
                        + "lang_code VARCHAR(16) NOT NULL,"
                        + "name VARCHAR(120) DEFAULT NULL,"
                        + "create_time DATETIME DEFAULT CURRENT_TIMESTAMP,"
                        + "update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                        + "PRIMARY KEY (id),"
                        + "UNIQUE KEY uk_category_lang (category_id, lang_code)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
        );
    }
}

