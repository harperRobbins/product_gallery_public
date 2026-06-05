package com.szwego.gallery.controller;

import com.szwego.gallery.common.ApiResponse;
import com.szwego.gallery.dto.ShopProfileSaveRequest;
import com.szwego.gallery.dto.ShopProfileVO;
import com.szwego.gallery.service.ShopProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ShopProfileController {

    private final ShopProfileService shopProfileService;

    @GetMapping("/api/shop/profile")
    public ApiResponse<ShopProfileVO> profile() {
        return ApiResponse.success(shopProfileService.getProfile());
    }

    @PostMapping("/api/admin/shop/profile")
    public ApiResponse<ShopProfileVO> save(@Validated @RequestBody ShopProfileSaveRequest request) {
        return ApiResponse.success("保存成功", shopProfileService.save(request));
    }
}
