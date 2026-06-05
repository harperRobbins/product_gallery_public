package com.szwego.gallery.service.impl;

import com.szwego.gallery.common.BusinessException;
import com.szwego.gallery.dto.AdminLoginVO;
import com.szwego.gallery.service.AdminAuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AdminAuthServiceImpl implements AdminAuthService {

    @Value("${app.admin.username:admin}")
    private String adminUsername;

    @Value("${app.admin.password:change_me}")
    private String adminPassword;

    @Value("${app.admin.token-expire-hours:24}")
    private Long tokenExpireHours;

    private final Map<String, TokenInfo> tokenStore = new ConcurrentHashMap<String, TokenInfo>();

    @Override
    public AdminLoginVO login(String username, String password) {
        if (!adminUsername.equals(username) || !adminPassword.equals(password)) {
            throw new BusinessException("账号或密码错误");
        }
        cleanupExpiredTokens();

        String token = UUID.randomUUID().toString().replace("-", "");
        long expiresAt = System.currentTimeMillis() + tokenExpireHours * 60L * 60L * 1000L;
        tokenStore.put(token, new TokenInfo(adminUsername, expiresAt));

        AdminLoginVO vo = new AdminLoginVO();
        vo.setToken(token);
        vo.setUsername(adminUsername);
        vo.setExpiresAt(expiresAt);
        return vo;
    }

    @Override
    public void logout(String token) {
        if (token == null || token.trim().isEmpty()) {
            return;
        }
        tokenStore.remove(token);
    }

    @Override
    public boolean isTokenValid(String token) {
        if (token == null || token.trim().isEmpty()) {
            return false;
        }
        TokenInfo info = tokenStore.get(token);
        if (info == null) {
            return false;
        }
        if (System.currentTimeMillis() > info.expiresAt) {
            tokenStore.remove(token);
            return false;
        }
        return true;
    }

    @Override
    public String getUsernameByToken(String token) {
        if (!isTokenValid(token)) {
            return null;
        }
        TokenInfo info = tokenStore.get(token);
        return info == null ? null : info.username;
    }

    @Override
    public String resolveToken(String authorizationHeader) {
        if (authorizationHeader == null) {
            return null;
        }
        String value = authorizationHeader.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (value.toLowerCase().startsWith("bearer ")) {
            return value.substring(7).trim();
        }
        return value;
    }

    private void cleanupExpiredTokens() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, TokenInfo> entry : tokenStore.entrySet()) {
            if (entry.getValue().expiresAt < now) {
                tokenStore.remove(entry.getKey());
            }
        }
    }

    private static class TokenInfo {
        private final String username;
        private final long expiresAt;

        private TokenInfo(String username, long expiresAt) {
            this.username = username;
            this.expiresAt = expiresAt;
        }
    }
}
