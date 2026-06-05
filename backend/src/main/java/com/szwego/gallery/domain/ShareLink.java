package com.szwego.gallery.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("share_link")
public class ShareLink extends BaseEntity {
    @TableId
    private Long id;
    private Long productId;
    private String shortCode;
    private String longUrl;
    private Integer visitCount;
}
