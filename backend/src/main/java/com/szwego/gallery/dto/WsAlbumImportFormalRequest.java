package com.szwego.gallery.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

@Data
public class WsAlbumImportFormalRequest {
    private List<Long> ids;

    /**
     * true: 一键导入全部（未导入+导入失败）
     * false/null: 导入指定 ids
     */
    private Boolean importAll;
    /**
     * 一键导入时可选指定店铺
     */
    private String shopId;

    @NotNull(message = "目标分类不能为空")
    private Long categoryId;

    private List<String> targetTags;
    private String priceStrategyType;
    private BigDecimal priceStrategyValue;
    private Integer allowRepeatImport;
    private String defaultTitleTemplate;
}
