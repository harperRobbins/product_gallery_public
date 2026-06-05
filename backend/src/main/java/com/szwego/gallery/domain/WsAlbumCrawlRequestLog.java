package com.szwego.gallery.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ws_album_crawl_request_log")
public class WsAlbumCrawlRequestLog extends BaseEntity {
    @TableId
    private Long id;
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
}

