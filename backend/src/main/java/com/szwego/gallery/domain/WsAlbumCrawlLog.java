package com.szwego.gallery.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ws_album_crawl_log")
public class WsAlbumCrawlLog extends BaseEntity {
    @TableId
    private Long id;
    private String crawlBatchNo;
    private String shopId;
    private String shopName;
    private String crawlMode;
    private String requestParamsJson;
    private String startDate;
    private String endDate;
    private String tagListJson;
    private String tagGroupId;
    private String keyword;
    private Long pageTimestampStart;
    private Long pageTimestampEnd;
    private Integer pageCount;
    private Integer fetchedCount;
    private Integer insertedCount;
    private Integer updatedCount;
    private Integer duplicateCount;
    private Integer failedCount;
    private Integer status;
    private String errorMessage;
    private String operatorId;
    private String operatorName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
