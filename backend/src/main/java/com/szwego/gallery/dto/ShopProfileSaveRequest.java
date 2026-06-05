package com.szwego.gallery.dto;

import lombok.Data;

import javax.validation.constraints.Size;
import java.util.List;

@Data
public class ShopProfileSaveRequest {
    @Size(max = 120, message = "店铺名称长度不能超过120")
    private String shopName;

    @Size(max = 1024, message = "店铺图标地址过长")
    private String shopLogo;

    @Size(max = 1024, message = "店铺banner地址过长")
    private String heroBanner;

    @Size(max = 255, message = "公告长度不能超过255")
    private String announcement;

    @Size(max = 255, message = "域名长度不能超过255")
    private String domain;

    @Size(max = 16, message = "语言标签长度不能超过16")
    private String languageLabel;

    @Size(max = 20, message = "主题色长度不能超过20")
    private String themeColor;

    @Size(max = 64, message = "联系人长度不能超过64")
    private String contactName;

    @Size(max = 64, message = "微信长度不能超过64")
    private String contactWechat;

    @Size(max = 64, message = "电话长度不能超过64")
    private String contactPhone;

    @Size(max = 255, message = "版权信息长度不能超过255")
    private String copyrightText;

    private List<ShopContactItemDTO> contacts;
    private List<ShopMenuItemDTO> menuItems;
    private List<ShopMenuPageConfigDTO> menuPageConfigs;
    private List<ShopCustomPageDTO> customPages;
    private List<ShopPageMetaDTO> pageMetas;
    private Integer blockSearchEngineCrawl;
}
