package com.szwego.gallery.config;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
@RequiredArgsConstructor
public class PerformanceIndexInitializer {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void init() {
        ensureIndex(
                "product",
                "idx_gallery_page",
                "ALTER TABLE product ADD INDEX idx_gallery_page (status, is_top, category_id, id)"
        );
        ensureIndex(
                "ws_album_product_import",
                "idx_formal_product_sort",
                "ALTER TABLE ws_album_product_import ADD INDEX idx_formal_product_sort "
                        + "(formal_product_id, is_deleted, source_update_time, new_send_time, time_stamp)"
        );
    }

    private void ensureIndex(String tableName, String indexName, String ddl) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.STATISTICS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?",
                Integer.class,
                tableName,
                indexName
        );
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.execute(ddl);
    }
}
