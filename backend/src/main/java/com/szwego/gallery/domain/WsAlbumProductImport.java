package com.szwego.gallery.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ws_album_product_import")
public class WsAlbumProductImport extends BaseEntity {
    @TableId
    private Long id;
    private String shopId;
    private String goodsId;
    private String title;
    private String subTitle;
    private String itemPrice;
    private String currencyCode;
    private Long newSendTime;
    private Long timeStamp;
    private Long sourceUpdateTime;
    private String shopName;
    private String sourceLink;
    private String tagGroupId;
    private String bizExtJson;
    private String rawJson;
    private Integer hasVideo;
    private String videoUrl;
    private String videoThumbImg;
    private String mainImageUrl;
    private Integer mediaCount;
    private Integer importStatus;
    private Long formalProductId;
    private String crawlBatchNo;
    private LocalDateTime firstCrawlTime;
    private LocalDateTime lastCrawlTime;
    private Integer isAbnormal;
    private String abnormalReason;
    @TableField("is_deleted")
    private Integer deleted;
}
