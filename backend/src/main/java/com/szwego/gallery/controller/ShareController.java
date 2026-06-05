package com.szwego.gallery.controller;

import com.szwego.gallery.common.ApiResponse;
import com.szwego.gallery.dto.ShareInfoVO;
import com.szwego.gallery.service.ShareService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ShareController {

    private final ShareService shareService;

    @PostMapping("/api/share/{productId}")
    public ApiResponse<ShareInfoVO> create(@PathVariable("productId") Long productId) {
        return ApiResponse.success(shareService.createShare(productId));
    }

    @GetMapping("/s/{shortCode}")
    public ResponseEntity<Void> redirect(@PathVariable("shortCode") String shortCode) {
        String longUrl = shareService.resolveLongUrl(shortCode);
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.LOCATION, longUrl);
        return new ResponseEntity<Void>(headers, HttpStatus.FOUND);
    }

    @GetMapping("/api/share/poster/{productId}")
    public ResponseEntity<byte[]> poster(@PathVariable("productId") Long productId) throws Exception {
        byte[] content = shareService.generatePoster(productId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.IMAGE_PNG);
        headers.setContentLength(content.length);
        return new ResponseEntity<byte[]>(content, headers, HttpStatus.OK);
    }
}

