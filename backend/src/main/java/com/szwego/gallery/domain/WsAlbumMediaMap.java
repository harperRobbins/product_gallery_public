package com.szwego.gallery.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ws_album_media_map")
public class WsAlbumMediaMap extends BaseEntity {
    @TableId
    private Long id;
    private String sourceUrl;
    private String ossKey;
    private String ossUrl;
    private String mediaType;
    private Integer refCount;
}
