ALTER TABLE product
    ADD INDEX idx_gallery_page (status, is_top, category_id, id);

ALTER TABLE ws_album_product_import
    ADD INDEX idx_formal_product_sort (formal_product_id, is_deleted, source_update_time, new_send_time, time_stamp);
