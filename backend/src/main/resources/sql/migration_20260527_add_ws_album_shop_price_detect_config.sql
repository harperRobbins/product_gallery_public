ALTER TABLE ws_album_shop
    ADD COLUMN IF NOT EXISTS price_detect_config_json LONGTEXT;
