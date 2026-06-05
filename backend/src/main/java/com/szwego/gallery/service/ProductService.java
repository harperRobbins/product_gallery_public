package com.szwego.gallery.service;

import com.szwego.gallery.common.PageResponse;
import com.szwego.gallery.dto.DashboardStatVO;
import com.szwego.gallery.dto.ProductCardVO;
import com.szwego.gallery.dto.ProductDetailVO;
import com.szwego.gallery.dto.ProductBatchCategoryRequest;
import com.szwego.gallery.dto.ProductPublishRequest;
import com.szwego.gallery.dto.ProductSummaryVO;

public interface ProductService {
    Long publish(ProductPublishRequest request);

    Long update(ProductPublishRequest request);

    void batchUpdateCategory(ProductBatchCategoryRequest request);

    void updateTopStatus(Long id, Integer isTop);

    void delete(Long id);

    ProductDetailVO detail(Long id, boolean increaseView, String lang);

    PageResponse<ProductCardVO> page(Long page, Long size, String keyword, String sku, Long categoryId, String tag, Integer status, Integer topOnly, String lang);

    ProductSummaryVO summary(Integer status);

    DashboardStatVO dashboardStats(boolean includeImageStorage);
}
