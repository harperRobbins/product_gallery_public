package com.szwego.gallery.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shop_profile")
public class ShopProfile extends BaseEntity {
    @TableId
    private Long id;
    private String shopName;
    private String shopLogo;
    private String heroBanner;
    private String announcement;
    private String domain;
    private String languageLabel;
    private String themeColor;
    private String contactName;
    private String contactWechat;
    private String contactPhone;
    private String contactConfigJson;
    private String copyrightText;
    private String menuConfigJson;
    private String menuPageConfigJson;
    private String customPagesJson;
    private String pageMetaJson;
    private Integer blockSearchEngineCrawl;
}
