package com.szwego.gallery.controller;

import com.szwego.gallery.common.ApiResponse;
import com.szwego.gallery.common.PageResponse;
import com.szwego.gallery.domain.WsAlbumShop;
import com.szwego.gallery.dto.WsAlbumConfigSaveRequest;
import com.szwego.gallery.dto.WsAlbumConfigTestRequest;
import com.szwego.gallery.dto.WsAlbumConfigVO;
import com.szwego.gallery.dto.WsAlbumCrawlLogVO;
import com.szwego.gallery.dto.WsAlbumCrawlRequestLogVO;
import com.szwego.gallery.dto.WsAlbumCrawlRetryRequest;
import com.szwego.gallery.dto.WsAlbumCrawlStartRequest;
import com.szwego.gallery.dto.WsAlbumCrawlStartVO;
import com.szwego.gallery.dto.WsAlbumCrawlStopRequest;
import com.szwego.gallery.dto.WsAlbumImportFormalRequest;
import com.szwego.gallery.dto.WsAlbumImportFormalResultVO;
import com.szwego.gallery.dto.WsAlbumImportLogVO;
import com.szwego.gallery.dto.WsAlbumProductImportDeleteRequest;
import com.szwego.gallery.dto.WsAlbumProductImportDetailVO;
import com.szwego.gallery.dto.WsAlbumProductImportMarkAbnormalRequest;
import com.szwego.gallery.dto.WsAlbumProductImportUpdateRequest;
import com.szwego.gallery.dto.WsAlbumProductImportVO;
import com.szwego.gallery.dto.WsAlbumShopAddRequest;
import com.szwego.gallery.dto.WsAlbumShopRefreshRequest;
import com.szwego.gallery.dto.WsAlbumShopUpdateRequest;
import com.szwego.gallery.dto.WsAlbumShopVO;
import com.szwego.gallery.service.AdminAuthService;
import com.szwego.gallery.service.WsAlbumService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class WsAlbumAdminController {

    private final WsAlbumService wsAlbumService;
    private final AdminAuthService adminAuthService;

    @GetMapping("/api/admin/ws-album/config")
    public ApiResponse<WsAlbumConfigVO> getConfig() {
        return ApiResponse.success(wsAlbumService.getConfig());
    }

    @PostMapping("/api/admin/ws-album/config/save")
    public ApiResponse<WsAlbumConfigVO> saveConfig(@Validated @RequestBody WsAlbumConfigSaveRequest request) {
        return ApiResponse.success("保存成功", wsAlbumService.saveConfig(request));
    }

    @PostMapping("/api/admin/ws-album/config/test")
    public ApiResponse<String> testConfig(@Validated @RequestBody WsAlbumConfigTestRequest request) {
        return ApiResponse.success(wsAlbumService.testConfig(request));
    }

    @GetMapping("/api/admin/ws-album/shop/list")
    public ApiResponse<PageResponse<WsAlbumShopVO>> listShops(@RequestParam(value = "page", defaultValue = "1") Long page,
                                                               @RequestParam(value = "size", defaultValue = "20") Long size,
                                                               @RequestParam(value = "keyword", required = false) String keyword,
                                                               @RequestParam(value = "status", required = false) Integer status) {
        return ApiResponse.success(wsAlbumService.listShops(page, size, keyword, status));
    }

    @PostMapping("/api/admin/ws-album/shop/add")
    public ApiResponse<WsAlbumShop> addShop(@Validated @RequestBody WsAlbumShopAddRequest request) {
        return ApiResponse.success("新增成功", wsAlbumService.addShop(request));
    }

    @PostMapping("/api/admin/ws-album/shop/update")
    public ApiResponse<WsAlbumShop> updateShop(@Validated @RequestBody WsAlbumShopUpdateRequest request) {
        return ApiResponse.success("更新成功", wsAlbumService.updateShop(request));
    }

    @GetMapping("/api/admin/ws-album/shop/detail")
    public ApiResponse<WsAlbumShop> detailShop(@RequestParam("shopId") String shopId) {
        return ApiResponse.success(wsAlbumService.detailShop(shopId));
    }

    @PostMapping("/api/admin/ws-album/shop/refresh")
    public ApiResponse<WsAlbumShop> refreshShop(@RequestBody WsAlbumShopRefreshRequest request) {
        return ApiResponse.success("刷新成功", wsAlbumService.refreshShop(request));
    }

    @PostMapping("/api/admin/ws-album/crawl/start")
    public ApiResponse<WsAlbumCrawlStartVO> startCrawl(@RequestBody WsAlbumCrawlStartRequest request,
                                                        @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.success("抓取任务已启动", wsAlbumService.startCrawl(request, resolveOperator(authorization)));
    }

    @GetMapping("/api/admin/ws-album/crawl/log/list")
    public ApiResponse<PageResponse<WsAlbumCrawlLogVO>> crawlLogList(@RequestParam(value = "page", defaultValue = "1") Long page,
                                                                      @RequestParam(value = "size", defaultValue = "20") Long size,
                                                                      @RequestParam(value = "shopId", required = false) String shopId,
                                                                      @RequestParam(value = "status", required = false) Integer status,
                                                                      @RequestParam(value = "crawlMode", required = false) String crawlMode) {
        return ApiResponse.success(wsAlbumService.listCrawlLogs(page, size, shopId, status, crawlMode));
    }

    @GetMapping("/api/admin/ws-album/crawl/log/detail")
    public ApiResponse<WsAlbumCrawlLogVO> crawlLogDetail(@RequestParam("crawlBatchNo") String crawlBatchNo) {
        return ApiResponse.success(wsAlbumService.crawlLogDetail(crawlBatchNo));
    }

    @GetMapping("/api/admin/ws-album/crawl/request-log/list")
    public ApiResponse<List<WsAlbumCrawlRequestLogVO>> crawlRequestLogList(@RequestParam("crawlBatchNo") String crawlBatchNo,
                                                                            @RequestParam(value = "limit", required = false) Integer limit) {
        return ApiResponse.success(wsAlbumService.listCrawlRequestLogs(crawlBatchNo, limit));
    }

    @PostMapping("/api/admin/ws-album/crawl/retry")
    public ApiResponse<WsAlbumCrawlStartVO> retryCrawl(@Validated @RequestBody WsAlbumCrawlRetryRequest request,
                                                        @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.success("重试任务已启动", wsAlbumService.retryCrawl(request, resolveOperator(authorization)));
    }

    @PostMapping("/api/admin/ws-album/crawl/stop")
    public ApiResponse<WsAlbumCrawlStartVO> stopCrawl(@Validated @RequestBody WsAlbumCrawlStopRequest request,
                                                       @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.success("停止指令已下发", wsAlbumService.stopCrawl(request, resolveOperator(authorization)));
    }

    @GetMapping("/api/admin/ws-album/product-import/list")
    public ApiResponse<PageResponse<WsAlbumProductImportVO>> listImportProducts(@RequestParam(value = "page", defaultValue = "1") Long page,
                                                                                 @RequestParam(value = "size", defaultValue = "20") Long size,
                                                                                 @RequestParam(value = "shopId", required = false) String shopId,
                                                                                 @RequestParam(value = "tagGroupId", required = false) String tagGroupId,
                                                                                 @RequestParam(value = "crawlBatchNo", required = false) String crawlBatchNo,
                                                                                 @RequestParam(value = "importStatus", required = false) Integer importStatus,
                                                                                 @RequestParam(value = "keyword", required = false) String keyword,
                                                                                 @RequestParam(value = "goodsId", required = false) String goodsId,
                                                                                 @RequestParam(value = "hasPrice", required = false) Integer hasPrice,
                                                                                 @RequestParam(value = "hasVideo", required = false) Integer hasVideo,
                                                                                 @RequestParam(value = "isAbnormal", required = false) Integer isAbnormal,
                                                                                 @RequestParam(value = "tagName", required = false) String tagName) {
        return ApiResponse.success(wsAlbumService.listImportProducts(page, size, shopId, tagGroupId, crawlBatchNo,
                importStatus, keyword, goodsId, hasPrice, hasVideo, isAbnormal, tagName));
    }

    @GetMapping("/api/admin/ws-album/product-import/detail")
    public ApiResponse<WsAlbumProductImportDetailVO> importProductDetail(@RequestParam("id") Long id) {
        return ApiResponse.success(wsAlbumService.importProductDetail(id));
    }

    @PostMapping("/api/admin/ws-album/product-import/update")
    public ApiResponse<Void> updateImportProduct(@Validated @RequestBody WsAlbumProductImportUpdateRequest request) {
        wsAlbumService.updateImportProduct(request);
        return ApiResponse.success("更新成功", null);
    }

    @PostMapping("/api/admin/ws-album/product-import/mark-abnormal")
    public ApiResponse<Void> markAbnormal(@Validated @RequestBody WsAlbumProductImportMarkAbnormalRequest request) {
        wsAlbumService.markImportProductAbnormal(request);
        return ApiResponse.success("操作成功", null);
    }

    @PostMapping("/api/admin/ws-album/product-import/delete")
    public ApiResponse<Void> deleteImportProducts(@Validated @RequestBody WsAlbumProductImportDeleteRequest request) {
        wsAlbumService.deleteImportProducts(request);
        return ApiResponse.success("删除成功", null);
    }

    @PostMapping("/api/admin/ws-album/product-import/import-formal")
    public ApiResponse<WsAlbumImportFormalResultVO> importFormal(@Validated @RequestBody WsAlbumImportFormalRequest request,
                                                                  @RequestHeader(value = "Authorization", required = false) String authorization) {
        return ApiResponse.success("导入任务已启动", wsAlbumService.importFormal(request, resolveOperator(authorization)));
    }

    @GetMapping("/api/admin/ws-album/import-log/list")
    public ApiResponse<PageResponse<WsAlbumImportLogVO>> importLogList(@RequestParam(value = "page", defaultValue = "1") Long page,
                                                                        @RequestParam(value = "size", defaultValue = "20") Long size,
                                                                        @RequestParam(value = "shopId", required = false) String shopId,
                                                                        @RequestParam(value = "status", required = false) Integer status) {
        return ApiResponse.success(wsAlbumService.listImportLogs(page, size, shopId, status));
    }

    @GetMapping("/api/admin/ws-album/import-log/detail")
    public ApiResponse<WsAlbumImportLogVO> importLogDetail(@RequestParam("importBatchNo") String importBatchNo) {
        return ApiResponse.success(wsAlbumService.importLogDetail(importBatchNo));
    }

    private String resolveOperator(String authorization) {
        String token = adminAuthService.resolveToken(authorization);
        String username = adminAuthService.getUsernameByToken(token);
        return username == null ? "admin" : username;
    }
}
