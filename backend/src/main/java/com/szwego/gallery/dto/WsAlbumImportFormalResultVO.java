package com.szwego.gallery.dto;

import lombok.Data;

@Data
public class WsAlbumImportFormalResultVO {
    private String importBatchNo;
    private Integer selectedCount;
    private Integer successCount;
    private Integer failedCount;
    private Integer skippedCount;
    private String status;
}
