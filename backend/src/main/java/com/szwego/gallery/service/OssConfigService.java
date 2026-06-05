package com.szwego.gallery.service;

import com.szwego.gallery.dto.OssConfigSaveRequest;
import com.szwego.gallery.dto.OssConfigTestRequest;
import com.szwego.gallery.dto.OssConfigVO;

public interface OssConfigService {

    OssConfigVO getConfig();

    OssConfigVO saveConfig(OssConfigSaveRequest request);

    String testConfig(OssConfigTestRequest request);

    void refreshRuntimeOssProperties();
}
