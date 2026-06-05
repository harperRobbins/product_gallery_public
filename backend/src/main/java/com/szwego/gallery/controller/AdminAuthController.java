package com.szwego.gallery.controller;

import com.szwego.gallery.common.ApiResponse;
import com.szwego.gallery.common.BusinessException;
import com.szwego.gallery.dto.AdminLoginRequest;
import com.szwego.gallery.dto.AdminLoginVO;
import com.szwego.gallery.service.AdminAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    @PostMapping("/api/admin/auth/login")
    public ApiResponse<AdminLoginVO> login(@Validated @RequestBody AdminLoginRequest request) {
        return ApiResponse.success("登录成功", adminAuthService.login(request.getUsername(), request.getPassword()));
    }

    @PostMapping("/api/admin/auth/logout")
    public ApiResponse<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String token = adminAuthService.resolveToken(authorization);
        adminAuthService.logout(token);
        return ApiResponse.success("退出成功", null);
    }

    @GetMapping("/api/admin/auth/me")
    public ApiResponse<Map<String, String>> me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        String token = adminAuthService.resolveToken(authorization);
        String username = adminAuthService.getUsernameByToken(token);
        if (username == null) {
            throw new BusinessException("登录已失效，请重新登录");
        }
        Map<String, String> data = new HashMap<String, String>();
        data.put("username", username);
        return ApiResponse.success(data);
    }
}
