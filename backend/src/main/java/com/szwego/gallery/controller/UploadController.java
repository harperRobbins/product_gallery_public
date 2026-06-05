package com.szwego.gallery.controller;

import com.szwego.gallery.common.ApiResponse;
import com.szwego.gallery.dto.UploadResultVO;
import com.szwego.gallery.service.UploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class UploadController {

    private final UploadService uploadService;

    @PostMapping("/api/admin/upload/images")
    public ApiResponse<UploadResultVO> upload(@RequestParam("files") MultipartFile[] files) {
        UploadResultVO vo = new UploadResultVO();
        vo.setUrls(uploadService.uploadImages(files));
        return ApiResponse.success("上传成功", vo);
    }

    @PostMapping("/api/admin/upload/videos")
    public ApiResponse<UploadResultVO> uploadVideos(@RequestParam("files") MultipartFile[] files) {
        UploadResultVO vo = new UploadResultVO();
        vo.setUrls(uploadService.uploadVideos(files));
        return ApiResponse.success("上传成功", vo);
    }
}
