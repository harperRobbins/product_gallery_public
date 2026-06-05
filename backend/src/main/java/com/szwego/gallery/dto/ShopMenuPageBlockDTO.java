package com.szwego.gallery.dto;

import lombok.Data;

@Data
public class ShopMenuPageBlockDTO {
    private String id;
    private String type;
    private String title;
    private String subTitle;
    private String imageUrl;
    private String content;
    private String buttonText;
    private String linkType;
    private String linkValue;
    private String startTime;
    private String endTime;
    private Integer sort;
    private Integer enabled;
}
