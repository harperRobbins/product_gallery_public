package com.szwego.gallery.service.impl;

import cn.hutool.core.util.StrUtil;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.szwego.gallery.common.BusinessException;
import com.szwego.gallery.config.OssProperties;
import com.szwego.gallery.domain.OssConfig;
import com.szwego.gallery.dto.OssConfigSaveRequest;
import com.szwego.gallery.dto.OssConfigTestRequest;
import com.szwego.gallery.dto.OssConfigVO;
import com.szwego.gallery.mapper.OssConfigMapper;
import com.szwego.gallery.service.OssConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OssConfigServiceImpl implements OssConfigService {

    private final OssConfigMapper ossConfigMapper;
    private final OssProperties ossProperties;
    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void init() {
        ensureConfigTable();
        refreshRuntimeOssProperties();
    }

    @Override
    public OssConfigVO getConfig() {
        OssConfig row = getLatestConfig();
        if (row == null) {
            return buildFromRuntimeProperties();
        }
        return toVO(row);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OssConfigVO saveConfig(OssConfigSaveRequest request) {
        if (request == null) {
            throw new BusinessException("请求参数不能为空");
        }

        OssConfig row = request.getId() == null ? getLatestConfig() : ossConfigMapper.selectById(request.getId());
        if (request.getId() != null && row == null) {
            throw new BusinessException("OSS配置不存在");
        }

        if (row == null) {
            row = new OssConfig();
            row.setEnabled(Boolean.TRUE.equals(ossProperties.getEnabled()) ? 1 : 0);
            row.setEndpoint(normalizeEndpoint(ossProperties.getEndpoint()));
            row.setBucketName(StrUtil.trimToNull(ossProperties.getBucketName()));
            row.setAccessKeyId(StrUtil.trimToNull(ossProperties.getAccessKeyId()));
            row.setAccessKeySecret(StrUtil.trimToNull(ossProperties.getAccessKeySecret()));
            row.setBucketDomain(normalizeBucketDomain(ossProperties.getBucketDomain()));
        }

        row.setEnabled(request.getEnabled() == null ? (row.getEnabled() == null ? 0 : normalizeFlag(row.getEnabled())) : normalizeFlag(request.getEnabled()));

        if (request.getEndpoint() != null) {
            row.setEndpoint(normalizeEndpoint(request.getEndpoint()));
        }
        if (request.getBucketName() != null) {
            row.setBucketName(StrUtil.trimToNull(request.getBucketName()));
        }
        if (request.getAccessKeyId() != null) {
            row.setAccessKeyId(StrUtil.trimToNull(request.getAccessKeyId()));
        }
        if (request.getAccessKeySecret() != null) {
            String newSecret = StrUtil.trimToNull(request.getAccessKeySecret());
            if (newSecret != null) {
                row.setAccessKeySecret(newSecret);
            } else if (row.getId() == null) {
                row.setAccessKeySecret(null);
            }
        }
        if (request.getBucketDomain() != null) {
            row.setBucketDomain(normalizeBucketDomain(request.getBucketDomain()));
        }

        validateConfig(row);

        if (row.getId() == null) {
            ossConfigMapper.insert(row);
        } else {
            ossConfigMapper.updateById(row);
        }

        refreshRuntimeOssProperties();
        return toVO(ossConfigMapper.selectById(row.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String testConfig(OssConfigTestRequest request) {
        if (request == null) {
            request = new OssConfigTestRequest();
        }

        RuntimeConfig runtime = buildRuntimeConfig(request);
        String endpoint = normalizeEndpoint(runtime.endpoint);
        OSS client = null;
        try {
            client = new OSSClientBuilder().build(endpoint, runtime.accessKeyId, runtime.accessKeySecret);
            boolean exists = client.doesBucketExist(runtime.bucketName);
            if (!exists) {
                persistTestStatus(false, "Bucket不存在: " + runtime.bucketName);
                throw new BusinessException("Bucket不存在: " + runtime.bucketName);
            }
            String verifyObjectKey = "product-gallery/healthcheck/" + UUID.randomUUID().toString().replace("-", "") + ".txt";
            client.putObject(runtime.bucketName, verifyObjectKey, new ByteArrayInputStream("ok".getBytes(StandardCharsets.UTF_8)));
            client.deleteObject(runtime.bucketName, verifyObjectKey);
            String okMsg = "连接成功: bucket=" + runtime.bucketName;
            persistTestStatus(true, okMsg);
            return okMsg;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            String err = normalizeError(ex);
            persistTestStatus(false, err);
            throw new BusinessException("OSS连通测试失败: " + err);
        } finally {
            if (client != null) {
                client.shutdown();
            }
        }
    }

    @Override
    public synchronized void refreshRuntimeOssProperties() {
        OssConfig row = getLatestConfig();
        if (row == null) {
            return;
        }
        ossProperties.setEnabled(row.getEnabled() != null && row.getEnabled() == 1);
        ossProperties.setEndpoint(normalizeEndpoint(row.getEndpoint()));
        ossProperties.setBucketName(StrUtil.trimToNull(row.getBucketName()));
        ossProperties.setAccessKeyId(StrUtil.trimToNull(row.getAccessKeyId()));
        ossProperties.setAccessKeySecret(StrUtil.trimToNull(row.getAccessKeySecret()));
        ossProperties.setBucketDomain(normalizeBucketDomain(row.getBucketDomain()));
    }

    private RuntimeConfig buildRuntimeConfig(OssConfigTestRequest request) {
        OssConfig saved = getLatestConfig();
        RuntimeConfig runtime = new RuntimeConfig();
        runtime.endpoint = normalizeEndpoint(StrUtil.blankToDefault(request.getEndpoint(), saved == null ? ossProperties.getEndpoint() : saved.getEndpoint()));
        runtime.bucketName = StrUtil.trimToNull(StrUtil.blankToDefault(request.getBucketName(), saved == null ? ossProperties.getBucketName() : saved.getBucketName()));
        runtime.accessKeyId = StrUtil.trimToNull(StrUtil.blankToDefault(request.getAccessKeyId(), saved == null ? ossProperties.getAccessKeyId() : saved.getAccessKeyId()));
        runtime.accessKeySecret = StrUtil.trimToNull(request.getAccessKeySecret());
        if (runtime.accessKeySecret == null) {
            runtime.accessKeySecret = StrUtil.trimToNull(saved == null ? ossProperties.getAccessKeySecret() : saved.getAccessKeySecret());
        }

        if (StrUtil.hasBlank(runtime.endpoint, runtime.bucketName, runtime.accessKeyId, runtime.accessKeySecret)) {
            throw new BusinessException("OSS测试参数不完整，请填写 endpoint/bucket/AK/SK");
        }
        return runtime;
    }

    private void validateConfig(OssConfig row) {
        if (row == null) {
            throw new BusinessException("OSS配置不能为空");
        }
        if (row.getEnabled() != null && row.getEnabled() == 1
                && StrUtil.hasBlank(row.getEndpoint(), row.getBucketName(), row.getAccessKeyId(), row.getAccessKeySecret())) {
            throw new BusinessException("启用OSS时必须填写 endpoint/bucket/AK/SK");
        }
    }

    private OssConfig getLatestConfig() {
        return ossConfigMapper.selectOne(new LambdaQueryWrapper<OssConfig>()
                .orderByDesc(OssConfig::getId)
                .last("limit 1"));
    }

    private OssConfigVO buildFromRuntimeProperties() {
        OssConfigVO vo = new OssConfigVO();
        vo.setEnabled(Boolean.TRUE.equals(ossProperties.getEnabled()) ? 1 : 0);
        vo.setEndpoint(normalizeEndpoint(ossProperties.getEndpoint()));
        vo.setBucketName(StrUtil.trimToNull(ossProperties.getBucketName()));
        vo.setAccessKeyId(StrUtil.trimToNull(ossProperties.getAccessKeyId()));
        vo.setAccessKeySecretMasked(maskSecret(ossProperties.getAccessKeySecret()));
        vo.setBucketDomain(normalizeBucketDomain(ossProperties.getBucketDomain()));
        return vo;
    }

    private OssConfigVO toVO(OssConfig row) {
        OssConfigVO vo = new OssConfigVO();
        vo.setId(row.getId());
        vo.setEnabled(row.getEnabled() == null ? 0 : normalizeFlag(row.getEnabled()));
        vo.setEndpoint(normalizeEndpoint(row.getEndpoint()));
        vo.setBucketName(StrUtil.trimToNull(row.getBucketName()));
        vo.setAccessKeyId(StrUtil.trimToNull(row.getAccessKeyId()));
        vo.setAccessKeySecretMasked(maskSecret(row.getAccessKeySecret()));
        vo.setBucketDomain(normalizeBucketDomain(row.getBucketDomain()));
        vo.setLastTestTime(row.getLastTestTime());
        vo.setLastTestStatus(row.getLastTestStatus());
        vo.setLastTestMessage(row.getLastTestMessage());
        return vo;
    }

    private String normalizeEndpoint(String endpoint) {
        if (StrUtil.isBlank(endpoint)) {
            return null;
        }
        String value = endpoint.trim();
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            value = "https://" + value;
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String normalizeBucketDomain(String bucketDomain) {
        if (StrUtil.isBlank(bucketDomain)) {
            return null;
        }
        String value = bucketDomain.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private int normalizeFlag(Integer value) {
        return value != null && value == 1 ? 1 : 0;
    }

    private String maskSecret(String secret) {
        String value = StrUtil.trimToNull(secret);
        if (value == null) {
            return "";
        }
        if (value.length() <= 4) {
            return "****";
        }
        return value.substring(0, 2) + "****" + value.substring(value.length() - 2);
    }

    private void persistTestStatus(boolean success, String message) {
        OssConfig row = getLatestConfig();
        if (row == null) {
            return;
        }
        row.setLastTestTime(LocalDateTime.now());
        row.setLastTestStatus(success ? 1 : 0);
        row.setLastTestMessage(StrUtil.sub(StrUtil.blankToDefault(message, ""), 0, 500));
        ossConfigMapper.updateById(row);
    }

    private void ensureConfigTable() {
        try {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS oss_config (" +
                    "id BIGINT NOT NULL," +
                    "enabled TINYINT NOT NULL DEFAULT 0," +
                    "endpoint VARCHAR(255) DEFAULT NULL," +
                    "bucket_name VARCHAR(128) DEFAULT NULL," +
                    "access_key_id VARCHAR(128) DEFAULT NULL," +
                    "access_key_secret VARCHAR(256) DEFAULT NULL," +
                    "bucket_domain VARCHAR(255) DEFAULT NULL," +
                    "last_test_time DATETIME DEFAULT NULL," +
                    "last_test_status TINYINT DEFAULT NULL," +
                    "last_test_message VARCHAR(512) DEFAULT NULL," +
                    "create_time DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                    "PRIMARY KEY (id)," +
                    "KEY idx_enabled (enabled)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        } catch (Exception ignored) {
            // 不阻断启动，表结构可通过schema.sql或手工迁移补齐。
        }
    }

    private String normalizeError(Throwable throwable) {
        if (throwable == null) {
            return "未知错误";
        }
        if (StrUtil.isNotBlank(throwable.getMessage())) {
            return throwable.getMessage();
        }
        return throwable.getClass().getSimpleName();
    }

    private static class RuntimeConfig {
        private String endpoint;
        private String bucketName;
        private String accessKeyId;
        private String accessKeySecret;
    }
}
