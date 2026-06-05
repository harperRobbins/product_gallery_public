package com.szwego.gallery.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class WsAlbumCrawlRetryRequest {
    @NotBlank(message = "crawlBatchNo不能为空")
    private String crawlBatchNo;
}
