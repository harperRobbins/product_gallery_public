package com.szwego.gallery.controller;

import com.szwego.gallery.common.ApiResponse;
import com.szwego.gallery.dto.OssConfigSaveRequest;
import com.szwego.gallery.dto.OssConfigTestRequest;
import com.szwego.gallery.dto.OssConfigVO;
import com.szwego.gallery.service.OssConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class OssConfigController {

    private final OssConfigService ossConfigService;

    @GetMapping("/api/admin/oss/config")
    public ApiResponse<OssConfigVO> getConfig() {
        return ApiResponse.success(ossConfigService.getConfig());
    }

    @PostMapping("/api/admin/oss/config/save")
    public ApiResponse<OssConfigVO> saveConfig(@RequestBody OssConfigSaveRequest request) {
        return ApiResponse.success("保存成功", ossConfigService.saveConfig(request));
    }

    @PostMapping("/api/admin/oss/config/test")
    public ApiResponse<String> testConfig(@RequestBody(required = false) OssConfigTestRequest request) {
        return ApiResponse.success(ossConfigService.testConfig(request));
    }
}
