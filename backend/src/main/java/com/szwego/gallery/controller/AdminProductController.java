package com.szwego.gallery.controller;

import com.szwego.gallery.common.ApiResponse;
import com.szwego.gallery.common.PageResponse;
import com.szwego.gallery.dto.ProductBatchCategoryRequest;
import com.szwego.gallery.dto.ProductCardVO;
import com.szwego.gallery.dto.ProductDetailVO;
import com.szwego.gallery.dto.ProductPublishRequest;
import com.szwego.gallery.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AdminProductController {

    private final ProductService productService;

    @PostMapping("/api/admin/products/publish")
    public ApiResponse<Long> publish(@Validated @RequestBody ProductPublishRequest request) {
        return ApiResponse.success("发布成功", productService.publish(request));
    }

    @PutMapping("/api/admin/products")
    public ApiResponse<Long> update(@Validated @RequestBody ProductPublishRequest request) {
        return ApiResponse.success("更新成功", productService.update(request));
    }

    @PostMapping("/api/admin/products/batch/category")
    public ApiResponse<Void> batchUpdateCategory(@Validated @RequestBody ProductBatchCategoryRequest request) {
        productService.batchUpdateCategory(request);
        return ApiResponse.success("批量修改成功", null);
    }

    @PostMapping("/api/admin/products/{id}/top")
    public ApiResponse<Void> updateTopStatus(@PathVariable("id") Long id,
                                             @RequestParam("isTop") Integer isTop) {
        productService.updateTopStatus(id, isTop);
        return ApiResponse.success("置顶状态更新成功", null);
    }

    @DeleteMapping("/api/admin/products/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        productService.delete(id);
        return ApiResponse.success(null);
    }

    @GetMapping("/api/admin/products/{id}")
    public ApiResponse<ProductDetailVO> detail(@PathVariable("id") Long id,
                                               @RequestParam(value = "lang", required = false) String lang) {
        return ApiResponse.success(productService.detail(id, false, lang));
    }

    @GetMapping("/api/admin/products")
    public ApiResponse<PageResponse<ProductCardVO>> page(@RequestParam(value = "page", defaultValue = "1") Long page,
                                                         @RequestParam(value = "size", defaultValue = "12") Long size,
                                                         @RequestParam(value = "keyword", required = false) String keyword,
                                                         @RequestParam(value = "sku", required = false) String sku,
                                                         @RequestParam(value = "categoryId", required = false) Long categoryId,
                                                         @RequestParam(value = "status", required = false) Integer status,
                                                         @RequestParam(value = "lang", required = false) String lang) {
        return ApiResponse.success(productService.page(page, size, keyword, sku, categoryId, null, status, null, lang));
    }
}
