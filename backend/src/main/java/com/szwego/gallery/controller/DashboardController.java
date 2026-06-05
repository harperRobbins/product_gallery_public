package com.szwego.gallery.controller;

import com.szwego.gallery.common.ApiResponse;
import com.szwego.gallery.dto.DashboardStatVO;
import com.szwego.gallery.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class DashboardController {

    private final ProductService productService;

    @GetMapping("/api/admin/dashboard/stats")
    public ApiResponse<DashboardStatVO> stats(@RequestParam(value = "includeImageStorage", defaultValue = "false") Boolean includeImageStorage) {
        return ApiResponse.success(productService.dashboardStats(Boolean.TRUE.equals(includeImageStorage)));
    }
}
