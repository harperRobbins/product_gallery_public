package com.szwego.gallery.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ws_album_shop")
public class WsAlbumShop extends BaseEntity {
    @TableId
    private Long id;
    private String shopId;
    private String shopName;
    private String albumId;
    private String icon;
    private String banner;
    private String qrcode;
    private String shareLink;
    private String cover;
    private String posterTitle;
    private Integer totalItemCount;
    private Integer isHasTag;
    private Integer isFollowed;
    private Integer hasVideo;
    private String priceDetectConfigJson;
    private String rawJson;
    private String remark;
    private Integer status;
    private LocalDateTime lastCrawlTime;
}
