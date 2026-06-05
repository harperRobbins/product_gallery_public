package com.szwego.gallery.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WsAlbumShopVO {
    private Long id;
    private String shopId;
    private String shopName;
    private String albumId;
    private String icon;
    private String banner;
    private String qrcode;
    private String shareLink;
    private Integer totalItemCount;
    private Integer isHasTag;
    private Integer hasVideo;
    private String priceDetectConfigJson;
    private Integer status;
    private String remark;
    private LocalDateTime lastCrawlTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
