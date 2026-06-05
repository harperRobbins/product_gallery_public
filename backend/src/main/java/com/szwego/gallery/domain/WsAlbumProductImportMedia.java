package com.szwego.gallery.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ws_album_product_import_media")
public class WsAlbumProductImportMedia extends BaseEntity {
    @TableId
    private Long id;
    private String shopId;
    private String goodsId;
    private String mediaType;
    private String sourceUrl;
    private String ossKey;
    private Integer sortNo;
    private Integer width;
    private Integer height;
    private Long sizeBytes;
    private Integer status;
}
