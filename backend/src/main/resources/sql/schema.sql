CREATE TABLE IF NOT EXISTS category (
    id BIGINT NOT NULL,
    parent_id BIGINT NOT NULL DEFAULT 0,
    name VARCHAR(64) NOT NULL,
    level INT NOT NULL DEFAULT 1,
    sort INT NOT NULL DEFAULT 0,
    path VARCHAR(255) DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_parent_id (parent_id),
    KEY idx_level (level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS product (
    id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    description LONGTEXT NOT NULL,
    category_id BIGINT NOT NULL,
    price DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    sku VARCHAR(100) NOT NULL,
    tags_json JSON,
    cover_image VARCHAR(1024) NOT NULL,
    video_url VARCHAR(1024) DEFAULT NULL,
    image_count INT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1=上架,0=下架',
    is_top TINYINT NOT NULL DEFAULT 0 COMMENT '1=置顶,0=普通',
    views INT NOT NULL DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sku (sku),
    KEY idx_category_id (category_id),
    KEY idx_status (status),
    KEY idx_is_top (is_top),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS product_image (
    id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    image_url VARCHAR(1024) NOT NULL,
    image_size_kb BIGINT NOT NULL DEFAULT 0,
    sort INT NOT NULL DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS product_i18n (
    id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    lang_code VARCHAR(16) NOT NULL,
    title VARCHAR(255) DEFAULT NULL,
    description TEXT DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_lang (product_id, lang_code),
    KEY idx_lang_title (lang_code, title)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS category_i18n (
    id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    lang_code VARCHAR(16) NOT NULL,
    name VARCHAR(120) DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_category_lang (category_id, lang_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS share_link (
    id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    short_code VARCHAR(20) NOT NULL,
    long_url VARCHAR(1024) NOT NULL,
    visit_count INT NOT NULL DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_short_code (short_code),
    KEY idx_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS order_voucher (
    id BIGINT NOT NULL,
    voucher_no VARCHAR(64) NOT NULL,
    public_code VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    customer_name VARCHAR(120) DEFAULT NULL,
    customer_contact_type VARCHAR(32) DEFAULT NULL,
    customer_contact_value VARCHAR(255) DEFAULT NULL,
    currency_code VARCHAR(16) DEFAULT NULL,
    item_count INT NOT NULL DEFAULT 0,
    subtotal_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    shipping_fee DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    discount_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    total_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    paid_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    balance_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    payment_status VARCHAR(16) NOT NULL DEFAULT 'UNPAID',
    remark VARCHAR(1000) DEFAULT NULL,
    internal_note VARCHAR(1000) DEFAULT NULL,
    expire_time DATETIME DEFAULT NULL,
    share_count INT NOT NULL DEFAULT 0,
    view_count INT NOT NULL DEFAULT 0,
    last_shared_time DATETIME DEFAULT NULL,
    last_view_time DATETIME DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_voucher_no (voucher_no),
    UNIQUE KEY uk_public_code (public_code),
    KEY idx_status (status),
    KEY idx_payment_status (payment_status),
    KEY idx_customer_name (customer_name),
    KEY idx_update_time (update_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS order_voucher_item (
    id BIGINT NOT NULL,
    voucher_id BIGINT NOT NULL,
    source_type VARCHAR(16) NOT NULL DEFAULT 'CUSTOM',
    product_id BIGINT DEFAULT NULL,
    product_title_snapshot VARCHAR(255) NOT NULL,
    product_sku_snapshot VARCHAR(100) DEFAULT NULL,
    cover_image_snapshot VARCHAR(1024) DEFAULT NULL,
    unit_price DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    quantity INT NOT NULL DEFAULT 1,
    line_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
    remark VARCHAR(500) DEFAULT NULL,
    sort INT NOT NULL DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_voucher_id (voucher_id),
    KEY idx_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS shop_profile (
    id BIGINT NOT NULL,
    shop_name VARCHAR(120) DEFAULT NULL,
    shop_logo VARCHAR(1024) DEFAULT NULL,
    hero_banner VARCHAR(1024) DEFAULT NULL,
    announcement VARCHAR(255) DEFAULT NULL,
    domain VARCHAR(255) DEFAULT NULL,
    language_label VARCHAR(16) DEFAULT NULL,
    theme_color VARCHAR(20) DEFAULT NULL,
    contact_name VARCHAR(64) DEFAULT NULL,
    contact_wechat VARCHAR(64) DEFAULT NULL,
    contact_phone VARCHAR(64) DEFAULT NULL,
    contact_config_json TEXT DEFAULT NULL,
    copyright_text VARCHAR(255) DEFAULT NULL,
    menu_config_json TEXT DEFAULT NULL,
    menu_page_config_json MEDIUMTEXT DEFAULT NULL,
    custom_pages_json MEDIUMTEXT DEFAULT NULL,
    page_meta_json MEDIUMTEXT DEFAULT NULL,
    block_search_engine_crawl TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ws_album_config (
    id BIGINT NOT NULL,
    config_name VARCHAR(64) DEFAULT NULL,
    token_enc VARCHAR(2048) NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    default_trans_lang VARCHAR(16) DEFAULT 'en',
    default_x_wg_lang VARCHAR(16) DEFAULT 'zh',
    default_max_pages INT NOT NULL DEFAULT 20,
    remark VARCHAR(255) DEFAULT NULL,
    last_verify_time DATETIME DEFAULT NULL,
    last_success_time DATETIME DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ws_album_shop (
    id BIGINT NOT NULL,
    shop_id VARCHAR(128) NOT NULL,
    shop_name VARCHAR(255) DEFAULT NULL,
    album_id VARCHAR(128) DEFAULT NULL,
    icon VARCHAR(1024) DEFAULT NULL,
    banner VARCHAR(1024) DEFAULT NULL,
    qrcode VARCHAR(1024) DEFAULT NULL,
    share_link VARCHAR(1024) DEFAULT NULL,
    cover VARCHAR(1024) DEFAULT NULL,
    poster_title VARCHAR(255) DEFAULT NULL,
    total_item_count INT DEFAULT 0,
    is_has_tag TINYINT NOT NULL DEFAULT 0,
    is_followed TINYINT NOT NULL DEFAULT 0,
    has_video TINYINT NOT NULL DEFAULT 0,
    price_detect_config_json LONGTEXT,
    raw_json LONGTEXT,
    remark VARCHAR(255) DEFAULT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    last_crawl_time DATETIME DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_shop_id (shop_id),
    KEY idx_album_id (album_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ws_album_shop_tag (
    id BIGINT NOT NULL,
    shop_id VARCHAR(128) NOT NULL,
    tag_id BIGINT NOT NULL,
    tag_name VARCHAR(128) DEFAULT NULL,
    raw_json LONGTEXT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_shop_tag (shop_id, tag_id),
    KEY idx_shop_id (shop_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ws_album_product_import (
    id BIGINT NOT NULL,
    shop_id VARCHAR(128) NOT NULL,
    goods_id VARCHAR(128) NOT NULL,
    title VARCHAR(255) DEFAULT NULL,
    sub_title VARCHAR(255) DEFAULT NULL,
    item_price VARCHAR(64) DEFAULT NULL,
    currency_code VARCHAR(16) DEFAULT NULL,
    new_send_time BIGINT DEFAULT NULL,
    time_stamp BIGINT DEFAULT NULL,
    source_update_time BIGINT DEFAULT NULL,
    shop_name VARCHAR(255) DEFAULT NULL,
    source_link VARCHAR(1024) DEFAULT NULL,
    tag_group_id VARCHAR(128) DEFAULT NULL,
    biz_ext_json LONGTEXT,
    raw_json LONGTEXT,
    has_video TINYINT NOT NULL DEFAULT 0,
    video_url VARCHAR(1024) DEFAULT NULL,
    video_thumb_img VARCHAR(1024) DEFAULT NULL,
    main_image_url VARCHAR(1024) DEFAULT NULL,
    media_count INT NOT NULL DEFAULT 0,
    import_status TINYINT NOT NULL DEFAULT 0 COMMENT '0=未导入,1=已导入,2=导入失败,3=已忽略',
    formal_product_id BIGINT DEFAULT NULL,
    crawl_batch_no VARCHAR(64) DEFAULT NULL,
    first_crawl_time DATETIME DEFAULT NULL,
    last_crawl_time DATETIME DEFAULT NULL,
    is_abnormal TINYINT NOT NULL DEFAULT 0,
    abnormal_reason VARCHAR(255) DEFAULT NULL,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_shop_goods (shop_id, goods_id),
    KEY idx_batch_no (crawl_batch_no),
    KEY idx_import_status (import_status),
    KEY idx_source_update_time (source_update_time),
    KEY idx_formal_product_deleted (formal_product_id, is_deleted),
    KEY idx_tag_group_id (tag_group_id),
    KEY idx_shop_id (shop_id),
    KEY idx_is_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ws_album_product_import_media (
    id BIGINT NOT NULL,
    shop_id VARCHAR(128) NOT NULL,
    goods_id VARCHAR(128) NOT NULL,
    media_type VARCHAR(32) NOT NULL,
    source_url VARCHAR(1024) NOT NULL,
    oss_key VARCHAR(1024) DEFAULT NULL,
    sort_no INT NOT NULL DEFAULT 0,
    width INT DEFAULT NULL,
    height INT DEFAULT NULL,
    size_bytes BIGINT DEFAULT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_goods_media (shop_id, goods_id),
    KEY idx_source_url (source_url(255))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ws_album_product_import_tag_rel (
    id BIGINT NOT NULL,
    shop_id VARCHAR(128) NOT NULL,
    goods_id VARCHAR(128) NOT NULL,
    tag_id BIGINT NOT NULL,
    tag_name VARCHAR(128) DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_goods_tag (shop_id, goods_id, tag_id),
    KEY idx_shop_goods (shop_id, goods_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ws_album_crawl_log (
    id BIGINT NOT NULL,
    crawl_batch_no VARCHAR(64) NOT NULL,
    shop_id VARCHAR(128) NOT NULL,
    shop_name VARCHAR(255) DEFAULT NULL,
    crawl_mode VARCHAR(32) NOT NULL,
    request_params_json LONGTEXT,
    start_date VARCHAR(32) DEFAULT NULL,
    end_date VARCHAR(32) DEFAULT NULL,
    tag_list_json LONGTEXT,
    tag_group_id VARCHAR(128) DEFAULT NULL,
    keyword VARCHAR(255) DEFAULT NULL,
    page_timestamp_start BIGINT DEFAULT NULL,
    page_timestamp_end BIGINT DEFAULT NULL,
    page_count INT NOT NULL DEFAULT 0,
    fetched_count INT NOT NULL DEFAULT 0,
    inserted_count INT NOT NULL DEFAULT 0,
    updated_count INT NOT NULL DEFAULT 0,
    duplicate_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0=执行中,1=成功,2=失败',
    error_message VARCHAR(1024) DEFAULT NULL,
    operator_id VARCHAR(64) DEFAULT NULL,
    operator_name VARCHAR(64) DEFAULT NULL,
    start_time DATETIME DEFAULT NULL,
    end_time DATETIME DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_crawl_batch_no (crawl_batch_no),
    KEY idx_shop_time (shop_id, create_time),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ws_album_crawl_request_log (
    id BIGINT NOT NULL,
    crawl_batch_no VARCHAR(64) NOT NULL,
    page_no INT NOT NULL DEFAULT 0,
    request_url VARCHAR(2048) NOT NULL,
    request_method VARCHAR(16) NOT NULL DEFAULT 'POST',
    request_params_json LONGTEXT,
    request_page_timestamp BIGINT DEFAULT NULL,
    response_page_timestamp BIGINT DEFAULT NULL,
    response_is_load_more TINYINT DEFAULT NULL,
    fetched_count INT DEFAULT NULL,
    http_status INT DEFAULT NULL,
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1=成功,2=失败',
    error_message VARCHAR(1024) DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_batch_page (crawl_batch_no, page_no),
    KEY idx_batch_id (crawl_batch_no, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ws_album_import_log (
    id BIGINT NOT NULL,
    import_batch_no VARCHAR(64) NOT NULL,
    crawl_batch_no VARCHAR(64) DEFAULT NULL,
    operator_id VARCHAR(64) DEFAULT NULL,
    operator_name VARCHAR(64) DEFAULT NULL,
    shop_id VARCHAR(128) DEFAULT NULL,
    selected_count INT NOT NULL DEFAULT 0,
    success_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    price_strategy_type VARCHAR(32) DEFAULT NULL,
    price_strategy_value DECIMAL(10,2) DEFAULT NULL,
    category_ids_json LONGTEXT,
    tag_ids_json LONGTEXT,
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0=执行中,1=成功,2=失败',
    error_message VARCHAR(1024) DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_import_batch_no (import_batch_no),
    KEY idx_shop_id (shop_id),
    KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS ws_album_media_map (
    id BIGINT NOT NULL,
    source_url VARCHAR(1024) NOT NULL,
    oss_key VARCHAR(1024) DEFAULT NULL,
    oss_url VARCHAR(1024) DEFAULT NULL,
    media_type VARCHAR(32) DEFAULT NULL,
    ref_count INT NOT NULL DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_source_url (source_url(255))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS llm_config (
    id BIGINT NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 0,
    provider VARCHAR(32) NOT NULL DEFAULT 'openai-compatible',
    base_url VARCHAR(255) DEFAULT NULL,
    api_key_enc VARCHAR(2048) DEFAULT NULL,
    model VARCHAR(128) DEFAULT NULL,
    temperature DECIMAL(4,2) DEFAULT 0.30,
    max_tokens INT DEFAULT 800,
    target_lang VARCHAR(16) DEFAULT 'en',
    strict_mode TINYINT NOT NULL DEFAULT 0,
    system_prompt TEXT,
    user_prompt_template TEXT,
    last_test_time DATETIME DEFAULT NULL,
    last_test_status TINYINT DEFAULT NULL,
    last_test_message VARCHAR(512) DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS oss_config (
    id BIGINT NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 0,
    endpoint VARCHAR(255) DEFAULT NULL,
    bucket_name VARCHAR(128) DEFAULT NULL,
    access_key_id VARCHAR(128) DEFAULT NULL,
    access_key_secret VARCHAR(256) DEFAULT NULL,
    bucket_domain VARCHAR(255) DEFAULT NULL,
    last_test_time DATETIME DEFAULT NULL,
    last_test_status TINYINT DEFAULT NULL,
    last_test_message VARCHAR(512) DEFAULT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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
