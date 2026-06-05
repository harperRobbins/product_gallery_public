package com.szwego.gallery.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.CannedAccessControlList;
import com.aliyun.oss.model.CreateBucketRequest;
import com.szwego.gallery.common.BusinessException;
import com.szwego.gallery.config.OssProperties;
import com.szwego.gallery.service.OssConfigService;
import com.szwego.gallery.service.UploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UploadServiceImpl implements UploadService {

    private static final Set<String> IMAGE_EXTENSIONS = new HashSet<String>();
    private static final Set<String> VIDEO_EXTENSIONS = new HashSet<String>();

    static {
        IMAGE_EXTENSIONS.add("jpg");
        IMAGE_EXTENSIONS.add("jpeg");
        IMAGE_EXTENSIONS.add("png");
        IMAGE_EXTENSIONS.add("webp");
        IMAGE_EXTENSIONS.add("gif");
        IMAGE_EXTENSIONS.add("bmp");

        VIDEO_EXTENSIONS.add("mp4");
        VIDEO_EXTENSIONS.add("mov");
        VIDEO_EXTENSIONS.add("m4v");
        VIDEO_EXTENSIONS.add("webm");
    }

    private final OssProperties ossProperties;
    private final OssConfigService ossConfigService;

    @Override
    public List<String> uploadImages(MultipartFile[] files) {
        validateFiles(files, "图片");
        validateImageFiles(files);
        return uploadToOss(files, "image");
    }

    @Override
    public List<String> uploadVideos(MultipartFile[] files) {
        validateFiles(files, "视频");
        validateVideoFiles(files);
        return uploadToOss(files, "video");
    }

    private void validateFiles(MultipartFile[] files, String label) {
        ossConfigService.refreshRuntimeOssProperties();
        if (files == null || files.length == 0) {
            throw new BusinessException("请选择要上传的" + label);
        }
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                throw new BusinessException("存在空文件，请重新上传");
            }
        }

        if (!Boolean.TRUE.equals(ossProperties.getEnabled())) {
            throw new BusinessException("当前仅支持OSS上传，请开启 OSS_ENABLED");
        }
    }

    private void validateImageFiles(MultipartFile[] files) {
        for (MultipartFile file : files) {
            String contentType = file.getContentType();
            String ext = safeExtension(file.getOriginalFilename());
            boolean contentTypeOk = contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("image/");
            if (!contentTypeOk && !IMAGE_EXTENSIONS.contains(ext)) {
                throw new BusinessException("仅支持上传图片文件");
            }
        }
    }

    private void validateVideoFiles(MultipartFile[] files) {
        for (MultipartFile file : files) {
            String contentType = file.getContentType();
            String ext = safeExtension(file.getOriginalFilename());
            boolean contentTypeOk = contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("video/");
            if (!contentTypeOk && !VIDEO_EXTENSIONS.contains(ext)) {
                throw new BusinessException("仅支持上传 MP4/MOV/M4V/WEBM 视频");
            }
        }
    }

    private List<String> uploadToOss(MultipartFile[] files, String mediaType) {
        String endpoint = ossProperties.getEndpoint();
        String bucketName = ossProperties.getBucketName();
        String accessKeyId = ossProperties.getAccessKeyId();
        String accessKeySecret = ossProperties.getAccessKeySecret();
        if (isBlank(endpoint) || isBlank(bucketName) || isBlank(accessKeyId) || isBlank(accessKeySecret)) {
            throw new BusinessException("OSS配置不完整，请先设置 endpoint、bucket、AK/SK");
        }

        OSS ossClient = null;
        List<String> urls = new ArrayList<String>();
        try {
            ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
            if (!ossClient.doesBucketExist(bucketName)) {
                CreateBucketRequest bucketRequest = new CreateBucketRequest(bucketName);
                bucketRequest.setCannedACL(CannedAccessControlList.PublicRead);
                ossClient.createBucket(bucketRequest);
            }
            String datePath = DateUtil.format(new Date(), "yyyy/MM/dd");
            for (MultipartFile file : files) {
                String suffix = FileUtil.extName(file.getOriginalFilename());
                if (!suffix.isEmpty()) {
                    suffix = "." + suffix;
                }
                String objectKey = "product-gallery/" + mediaType + "/" + datePath + "/" + IdUtil.fastSimpleUUID() + suffix;
                ossClient.putObject(bucketName, objectKey, file.getInputStream());
                ossClient.setObjectAcl(bucketName, objectKey, CannedAccessControlList.PublicRead);
                urls.add(buildOssUrl(bucketName, endpoint, objectKey, ossProperties.getBucketDomain()));
            }
            return urls;
        } catch (IOException e) {
            throw new BusinessException("上传文件失败: " + e.getMessage());
        } catch (Exception e) {
            throw new BusinessException("上传到OSS失败: " + e.getMessage());
        } finally {
            if (ossClient != null) {
                ossClient.shutdown();
            }
        }
    }

    private String safeExtension(String filename) {
        if (filename == null) {
            return "";
        }
        String ext = FileUtil.extName(filename);
        if (ext == null) {
            return "";
        }
        return ext.toLowerCase(Locale.ROOT);
    }

    private String buildOssUrl(String bucketName, String endpoint, String objectKey, String bucketDomain) {
        if (!isBlank(bucketDomain)) {
            if (bucketDomain.startsWith("http://") || bucketDomain.startsWith("https://")) {
                return bucketDomain + "/" + objectKey;
            }
            return "https://" + bucketDomain + "/" + objectKey;
        }
        String endpointHost = endpoint.replace("https://", "").replace("http://", "");
        return "https://" + bucketName + "." + endpointHost + "/" + objectKey;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
