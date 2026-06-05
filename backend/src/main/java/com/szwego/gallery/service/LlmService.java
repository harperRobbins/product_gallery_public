package com.szwego.gallery.service;

import com.szwego.gallery.dto.LlmConfigSaveRequest;
import com.szwego.gallery.dto.LlmConfigTestRequest;
import com.szwego.gallery.dto.LlmConfigVO;
import com.szwego.gallery.dto.LlmTranslationResult;

public interface LlmService {

    LlmConfigVO getConfig();

    LlmConfigVO saveConfig(LlmConfigSaveRequest request);

    LlmTranslationResult testConfig(LlmConfigTestRequest request);

    LlmTranslationResult summarizeAndTranslateToEnglish(String title, String description);

    LlmTranslationResult summarizeAndTranslateBilingual(String title, String description);
}
