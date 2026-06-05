package com.szwego.gallery.service;

import com.szwego.gallery.dto.AdminLoginVO;

public interface AdminAuthService {
    AdminLoginVO login(String username, String password);

    void logout(String token);

    boolean isTokenValid(String token);

    String getUsernameByToken(String token);

    String resolveToken(String authorizationHeader);
}
