package com.szwego.gallery.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class WsAlbumImportLogVO {
    private String importBatchNo;
    private String crawlBatchNo;
    private String shopId;
    private Integer selectedCount;
    private Integer successCount;
    private Integer failedCount;
    private String priceStrategyType;
    private BigDecimal priceStrategyValue;
    private Integer status;
    private String errorMessage;
    private LocalDateTime createTime;
}
