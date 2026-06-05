package com.szwego.gallery.service;

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

import java.util.List;

public interface WsAlbumService {
    WsAlbumConfigVO getConfig();

    WsAlbumConfigVO saveConfig(WsAlbumConfigSaveRequest request);

    String testConfig(WsAlbumConfigTestRequest request);

    PageResponse<WsAlbumShopVO> listShops(Long page, Long size, String keyword, Integer status);

    WsAlbumShop addShop(WsAlbumShopAddRequest request);

    WsAlbumShop updateShop(WsAlbumShopUpdateRequest request);

    WsAlbumShop detailShop(String shopId);

    WsAlbumShop refreshShop(WsAlbumShopRefreshRequest request);

    WsAlbumCrawlStartVO startCrawl(WsAlbumCrawlStartRequest request, String operatorName);

    WsAlbumCrawlStartVO retryCrawl(WsAlbumCrawlRetryRequest request, String operatorName);

    WsAlbumCrawlStartVO stopCrawl(WsAlbumCrawlStopRequest request, String operatorName);

    PageResponse<WsAlbumCrawlLogVO> listCrawlLogs(Long page, Long size, String shopId, Integer status, String crawlMode);

    WsAlbumCrawlLogVO crawlLogDetail(String crawlBatchNo);

    List<WsAlbumCrawlRequestLogVO> listCrawlRequestLogs(String crawlBatchNo, Integer limit);

    PageResponse<WsAlbumProductImportVO> listImportProducts(Long page,
                                                            Long size,
                                                            String shopId,
                                                            String tagGroupId,
                                                            String crawlBatchNo,
                                                            Integer importStatus,
                                                            String keyword,
                                                            String goodsId,
                                                            Integer hasPrice,
                                                            Integer hasVideo,
                                                            Integer isAbnormal,
                                                            String tagName);

    WsAlbumProductImportDetailVO importProductDetail(Long id);

    void updateImportProduct(WsAlbumProductImportUpdateRequest request);

    void markImportProductAbnormal(WsAlbumProductImportMarkAbnormalRequest request);

    void deleteImportProducts(WsAlbumProductImportDeleteRequest request);

    WsAlbumImportFormalResultVO importFormal(WsAlbumImportFormalRequest request, String operatorName);

    PageResponse<WsAlbumImportLogVO> listImportLogs(Long page, Long size, String shopId, Integer status);

    WsAlbumImportLogVO importLogDetail(String importBatchNo);
}
