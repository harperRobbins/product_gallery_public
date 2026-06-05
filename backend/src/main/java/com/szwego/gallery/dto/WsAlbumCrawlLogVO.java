package com.szwego.gallery.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WsAlbumCrawlLogVO {
    private String crawlBatchNo;
    private String shopId;
    private String shopName;
    private String crawlMode;
    private String requestParamsJson;
    private String tagGroupId;
    private String startDate;
    private String endDate;
    private Integer pageCount;
    private Integer fetchedCount;
    private Integer insertedCount;
    private Integer updatedCount;
    private Integer duplicateCount;
    private Integer failedCount;
    private Integer status;
    private String errorMessage;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime createTime;
}
