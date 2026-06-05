package com.szwego.gallery.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ws_album_import_log")
public class WsAlbumImportLog {
    @TableId
    private Long id;
    private String importBatchNo;
    private String crawlBatchNo;
    private String operatorId;
    private String operatorName;
    private String shopId;
    private Integer selectedCount;
    private Integer successCount;
    private Integer failedCount;
    private String priceStrategyType;
    private java.math.BigDecimal priceStrategyValue;
    private String categoryIdsJson;
    private String tagIdsJson;
    private Integer status;
    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
