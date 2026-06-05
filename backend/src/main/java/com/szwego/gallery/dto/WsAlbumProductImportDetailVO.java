package com.szwego.gallery.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class WsAlbumProductImportDetailVO {
    private Long id;
    private String shopId;
    private String shopName;
    private String goodsId;
    private String title;
    private String subTitle;
    private String itemPrice;
    private String currencyCode;
    private Long newSendTime;
    private Long timeStamp;
    private Long sourceUpdateTime;
    private String sourceLink;
    private String tagGroupId;
    private Integer hasVideo;
    private String videoUrl;
    private String videoThumbImg;
    private String mainImageUrl;
    private Integer mediaCount;
    private Integer importStatus;
    private Long formalProductId;
    private String crawlBatchNo;
    private Integer isAbnormal;
    private String abnormalReason;
    private LocalDateTime firstCrawlTime;
    private LocalDateTime lastCrawlTime;
    private String bizExtJson;
    private String rawJson;
    private List<String> imageUrls;
    private List<String> sourceTags;
}
