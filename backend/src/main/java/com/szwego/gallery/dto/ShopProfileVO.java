package com.szwego.gallery.dto;

import lombok.Data;

import java.util.List;

@Data
public class ShopProfileVO {
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
    private List<ShopContactItemDTO> contacts;
    private String copyrightText;
    private List<ShopMenuItemDTO> menuItems;
    private List<ShopMenuPageConfigDTO> menuPageConfigs;
    private List<ShopCustomPageDTO> customPages;
    private List<ShopPageMetaDTO> pageMetas;
    private Integer blockSearchEngineCrawl;
}
