package com.szwego.gallery.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface UploadService {
    List<String> uploadImages(MultipartFile[] files);

    List<String> uploadVideos(MultipartFile[] files);
}
