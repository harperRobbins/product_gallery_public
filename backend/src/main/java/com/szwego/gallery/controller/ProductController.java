package com.szwego.gallery.controller;

import com.szwego.gallery.common.ApiResponse;
import com.szwego.gallery.common.PageResponse;
import com.szwego.gallery.dto.ProductCardVO;
import com.szwego.gallery.dto.ProductDetailVO;
import com.szwego.gallery.dto.ProductSummaryVO;
import com.szwego.gallery.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping("/api/products")
    public ApiResponse<PageResponse<ProductCardVO>> page(@RequestParam(value = "page", defaultValue = "1") Long page,
                                                         @RequestParam(value = "size", defaultValue = "12") Long size,
                                                         @RequestParam(value = "keyword", required = false) String keyword,
                                                         @RequestParam(value = "sku", required = false) String sku,
                                                         @RequestParam(value = "categoryId", required = false) Long categoryId,
                                                         @RequestParam(value = "tag", required = false) String tag,
                                                         @RequestParam(value = "topOnly", required = false) Integer topOnly,
                                                         @RequestParam(value = "lang", required = false) String lang) {
        return ApiResponse.success(productService.page(page, size, keyword, sku, categoryId, tag, 1, topOnly, lang));
    }

    @GetMapping("/api/products/{id:\\d+}")
    public ApiResponse<ProductDetailVO> detail(@PathVariable("id") Long id,
                                               @RequestParam(value = "lang", required = false) String lang) {
        return ApiResponse.success(productService.detail(id, true, lang));
    }

    @GetMapping("/api/products/summary")
    public ApiResponse<ProductSummaryVO> summary() {
        return ApiResponse.success(productService.summary(1));
    }
}
