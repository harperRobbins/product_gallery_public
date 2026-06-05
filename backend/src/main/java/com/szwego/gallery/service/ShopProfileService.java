package com.szwego.gallery.service;

import com.szwego.gallery.dto.ShopProfileSaveRequest;
import com.szwego.gallery.dto.ShopProfileVO;

public interface ShopProfileService {
    ShopProfileVO getProfile();

    ShopProfileVO save(ShopProfileSaveRequest request);
}
