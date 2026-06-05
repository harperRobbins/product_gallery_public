package com.szwego.gallery.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WsAlbumCrawlRequestLogVO {
    private String crawlBatchNo;
    private Integer pageNo;
    private String requestUrl;
    private String requestMethod;
    private String requestParamsJson;
    private Long requestPageTimestamp;
    private Long responsePageTimestamp;
    private Integer responseIsLoadMore;
    private Integer fetchedCount;
    private Integer httpStatus;
    private Integer status;
    private String errorMessage;
    private LocalDateTime createTime;
}

