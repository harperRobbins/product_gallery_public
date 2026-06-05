package com.szwego.gallery.dto;

import lombok.Data;

import java.util.List;

@Data
public class WsAlbumCrawlStartRequest {
    private String shopId;
    private String albumId;
    private String crawlMode;
    private Long startTimestamp;
    private String startDate;
    private String endDate;
    private List<Long> tagList;
    private String tagGroupId;
    private String searchValue;
    private Integer maxPages;
    private Integer enableTimestampUpdate;
    private String remark;
    private List<String> itemIds;
    private Integer mergeItemIds;
}
