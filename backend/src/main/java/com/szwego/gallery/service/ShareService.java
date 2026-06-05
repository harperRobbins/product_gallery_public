package com.szwego.gallery.service;

import com.szwego.gallery.dto.ShareInfoVO;

import java.io.IOException;

public interface ShareService {
    ShareInfoVO createShare(Long productId);

    String resolveLongUrl(String shortCode);

    byte[] generatePoster(Long productId) throws IOException;
}
