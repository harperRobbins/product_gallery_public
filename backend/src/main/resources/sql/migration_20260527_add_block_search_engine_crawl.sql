ALTER TABLE shop_profile
    ADD COLUMN IF NOT EXISTS block_search_engine_crawl TINYINT NOT NULL DEFAULT 0;
