package com.szwego.gallery.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ws_album_shop_tag")
public class WsAlbumShopTag extends BaseEntity {
    @TableId
    private Long id;
    private String shopId;
    private Long tagId;
    private String tagName;
    private String rawJson;
}
