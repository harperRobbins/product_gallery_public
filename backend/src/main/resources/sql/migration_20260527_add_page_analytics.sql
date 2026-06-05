CREATE TABLE IF NOT EXISTS analytics_page_daily_stat (
    stat_date DATE NOT NULL,
    page_path VARCHAR(255) NOT NULL,
    page_title VARCHAR(255) DEFAULT NULL,
    pv_count BIGINT NOT NULL DEFAULT 0,
    uv_count BIGINT NOT NULL DEFAULT 0,
    leave_count BIGINT NOT NULL DEFAULT 0,
    total_stay_seconds BIGINT NOT NULL DEFAULT 0,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (stat_date, page_path),
    KEY idx_page_path (page_path),
    KEY idx_page_date (page_path, stat_date),
    KEY idx_update_time (update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS analytics_page_daily_visitor (
    stat_date DATE NOT NULL,
    page_path VARCHAR(255) NOT NULL,
    visitor_hash VARCHAR(64) NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (stat_date, page_path, visitor_hash),
    KEY idx_page_visitor (page_path, visitor_hash),
    KEY idx_page_date_visitor (page_path, stat_date, visitor_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
