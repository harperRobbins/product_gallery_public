package com.szwego.gallery.dto;

import lombok.Data;

@Data
public class AdminLoginVO {
    private String token;
    private String username;
    private Long expiresAt;
}
