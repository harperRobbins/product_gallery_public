package com.szwego.gallery.controller;

import com.szwego.gallery.common.ApiResponse;
import com.szwego.gallery.dto.LlmConfigSaveRequest;
import com.szwego.gallery.dto.LlmConfigTestRequest;
import com.szwego.gallery.dto.LlmConfigVO;
import com.szwego.gallery.dto.LlmTranslationResult;
import com.szwego.gallery.service.LlmService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class LlmConfigController {

    private final LlmService llmService;

    @GetMapping("/api/admin/llm/config")
    public ApiResponse<LlmConfigVO> getConfig() {
        return ApiResponse.success(llmService.getConfig());
    }

    @PostMapping("/api/admin/llm/config/save")
    public ApiResponse<LlmConfigVO> saveConfig(@RequestBody LlmConfigSaveRequest request) {
        return ApiResponse.success("保存成功", llmService.saveConfig(request));
    }

    @PostMapping("/api/admin/llm/config/test")
    public ApiResponse<LlmTranslationResult> testConfig(@RequestBody LlmConfigTestRequest request) {
        if (request == null) {
            request = new LlmConfigTestRequest();
        }
        return ApiResponse.success(llmService.testConfig(request));
    }
}
