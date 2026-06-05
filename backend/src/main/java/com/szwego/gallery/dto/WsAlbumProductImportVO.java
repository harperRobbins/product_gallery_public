package com.szwego.gallery.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class WsAlbumProductImportVO {
    private Long id;
    private String shopId;
    private String shopName;
    private String goodsId;
    private String title;
    private String itemPrice;
    private String mainImageUrl;
    private Integer mediaCount;
    private Integer hasVideo;
    private Long newSendTime;
    private Long sourceUpdateTime;
    private String crawlBatchNo;
    private Integer importStatus;
    private Long formalProductId;
    private Integer isAbnormal;
    private LocalDateTime lastCrawlTime;
    private List<String> sourceTags;
}
