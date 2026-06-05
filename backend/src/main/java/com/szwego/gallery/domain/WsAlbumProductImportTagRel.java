package com.szwego.gallery.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ws_album_product_import_tag_rel")
public class WsAlbumProductImportTagRel extends BaseEntity {
    @TableId
    private Long id;
    private String shopId;
    private String goodsId;
    private Long tagId;
    private String tagName;
}
