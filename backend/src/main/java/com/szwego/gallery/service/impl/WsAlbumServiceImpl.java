package com.szwego.gallery.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.CannedAccessControlList;
import com.aliyun.oss.model.CreateBucketRequest;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.szwego.gallery.common.BusinessException;
import com.szwego.gallery.common.PageResponse;
import com.szwego.gallery.config.OssProperties;
import com.szwego.gallery.config.WsAlbumProperties;
import com.szwego.gallery.domain.Product;
import com.szwego.gallery.domain.ProductI18n;
import com.szwego.gallery.domain.ProductImage;
import com.szwego.gallery.domain.WsAlbumConfig;
import com.szwego.gallery.domain.WsAlbumCrawlLog;
import com.szwego.gallery.domain.WsAlbumCrawlRequestLog;
import com.szwego.gallery.domain.WsAlbumImportLog;
import com.szwego.gallery.domain.WsAlbumMediaMap;
import com.szwego.gallery.domain.WsAlbumProductImport;
import com.szwego.gallery.domain.WsAlbumProductImportMedia;
import com.szwego.gallery.domain.WsAlbumProductImportTagRel;
import com.szwego.gallery.domain.WsAlbumShop;
import com.szwego.gallery.domain.WsAlbumShopTag;
import com.szwego.gallery.dto.WsAlbumConfigSaveRequest;
import com.szwego.gallery.dto.WsAlbumConfigTestRequest;
import com.szwego.gallery.dto.WsAlbumConfigVO;
import com.szwego.gallery.dto.WsAlbumCrawlLogVO;
import com.szwego.gallery.dto.WsAlbumCrawlRequestLogVO;
import com.szwego.gallery.dto.WsAlbumCrawlRetryRequest;
import com.szwego.gallery.dto.WsAlbumCrawlStartRequest;
import com.szwego.gallery.dto.WsAlbumCrawlStartVO;
import com.szwego.gallery.dto.WsAlbumCrawlStopRequest;
import com.szwego.gallery.dto.WsAlbumImportFormalRequest;
import com.szwego.gallery.dto.WsAlbumImportFormalResultVO;
import com.szwego.gallery.dto.WsAlbumImportLogVO;
import com.szwego.gallery.dto.LlmTranslationResult;
import com.szwego.gallery.dto.WsAlbumProductImportDeleteRequest;
import com.szwego.gallery.dto.WsAlbumProductImportDetailVO;
import com.szwego.gallery.dto.WsAlbumProductImportMarkAbnormalRequest;
import com.szwego.gallery.dto.WsAlbumProductImportUpdateRequest;
import com.szwego.gallery.dto.WsAlbumProductImportVO;
import com.szwego.gallery.dto.WsAlbumShopAddRequest;
import com.szwego.gallery.dto.WsAlbumShopRefreshRequest;
import com.szwego.gallery.dto.WsAlbumShopUpdateRequest;
import com.szwego.gallery.dto.WsAlbumShopVO;
import com.szwego.gallery.mapper.CategoryMapper;
import com.szwego.gallery.mapper.ProductImageMapper;
import com.szwego.gallery.mapper.ProductI18nMapper;
import com.szwego.gallery.mapper.ProductMapper;
import com.szwego.gallery.mapper.WsAlbumConfigMapper;
import com.szwego.gallery.mapper.WsAlbumCrawlLogMapper;
import com.szwego.gallery.mapper.WsAlbumCrawlRequestLogMapper;
import com.szwego.gallery.mapper.WsAlbumImportLogMapper;
import com.szwego.gallery.mapper.WsAlbumMediaMapMapper;
import com.szwego.gallery.mapper.WsAlbumProductImportMapper;
import com.szwego.gallery.mapper.WsAlbumProductImportMediaMapper;
import com.szwego.gallery.mapper.WsAlbumProductImportTagRelMapper;
import com.szwego.gallery.mapper.WsAlbumShopMapper;
import com.szwego.gallery.mapper.WsAlbumShopTagMapper;
import com.szwego.gallery.service.WsAlbumService;
import com.szwego.gallery.service.LlmService;
import com.szwego.gallery.service.OssConfigService;
import com.szwego.gallery.util.LanguageUtil;
import com.szwego.gallery.util.PageUtil;
import com.szwego.gallery.util.TagUtil;
import com.szwego.gallery.util.WsAlbumTokenCryptoUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.security.MessageDigest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WsAlbumServiceImpl implements WsAlbumService {

    private static final int CRAWL_STATUS_RUNNING = 0;
    private static final int CRAWL_STATUS_SUCCESS = 1;
    private static final int CRAWL_STATUS_FAILED = 2;
    private static final int CRAWL_STATUS_STOPPED = 3;

    private static final int IMPORT_STATUS_PENDING = 0;
    private static final int IMPORT_STATUS_SUCCESS = 1;
    private static final int IMPORT_STATUS_FAILED = 2;
    private static final int IMPORT_STATUS_IGNORED = 3;
    private static final int IMPORT_LOG_STATUS_RUNNING = 0;
    private static final int IMPORT_LOG_STATUS_SUCCESS = 1;
    private static final int IMPORT_LOG_STATUS_FAILED = 2;
    private static final int IMPORT_TITLE_SAFE_MAX = 240;
    private static final int IMPORT_SUBTITLE_SAFE_MAX = 240;
    private static final Pattern PRICE_PATTERN_P = Pattern.compile("(?i)\\bP\\s*([1-9]\\d{1,5}(?:\\.\\d{1,2})?)\\b");
    private static final Pattern PRICE_PATTERN_CURRENCY_PREFIX = Pattern.compile("(?i)(?:¥|￥|RMB|CNY|USD|US\\$|\\$)\\s*([1-9]\\d{1,6}(?:\\.\\d{1,2})?)");
    private static final Pattern PRICE_PATTERN_CURRENCY_SUFFIX = Pattern.compile("(?i)([1-9]\\d{1,6}(?:\\.\\d{1,2})?)\\s*(?:元|块|RMB|CNY|USD|dollars?)\\b");
    private static final Pattern PRICE_TOKEN_PATTERN_P = Pattern.compile("(?i)\\bP\\s*[1-9]\\d{1,5}(?:\\.\\d{1,2})?\\b");
    private static final Pattern PRICE_TOKEN_PATTERN_CURRENCY_PREFIX = Pattern.compile("(?i)(?:¥|￥|RMB|CNY|USD|US\\$|\\$)\\s*[1-9]\\d{1,6}(?:\\.\\d{1,2})?");
    private static final Pattern PRICE_TOKEN_PATTERN_CURRENCY_SUFFIX = Pattern.compile("(?i)\\b[1-9]\\d{1,6}(?:\\.\\d{1,2})?\\s*(?:元|块|RMB|CNY|USD|dollars?)\\b");
    private static final Pattern PRICE_TOKEN_PATTERN_PRICE_WORD = Pattern.compile("(?i)(?:价格|售价|价位|price)\\s*[:：]?\\s*[1-9]\\d{1,6}(?:\\.\\d{1,2})?");
    private static final String PRICE_PRIORITY_ITEM_FIRST = "ITEM_FIRST";
    private static final String PRICE_PRIORITY_TEXT_FIRST = "TEXT_FIRST";
    private static final String PRICE_PRIORITY_TEXT_ONLY = "TEXT_ONLY";
    private static final String PRICE_PRIORITY_ITEM_ONLY = "ITEM_ONLY";
    private static final String PRICE_MATCH_FIRST_HIT = "FIRST_HIT";
    private static final String PRICE_MATCH_MAX = "MAX";
    private static final String PRICE_MATCH_MIN = "MIN";
    private static final String PRICE_TRANSFORM_NONE = "NONE";
    private static final String PRICE_TRANSFORM_REVERSE_LAST_N = "REVERSE_LAST_N";
    private static final Pattern CODE_LABELED_PATTERN = Pattern.compile("(?i)(?:款号|型号|货号|编号|code|style\\s*no|model)\\s*[:：#-]?\\s*([A-Za-z0-9][A-Za-z0-9-]{2,24})");
    private static final Pattern CODE_ALNUM_PATTERN = Pattern.compile("(?<![A-Za-z0-9])[A-Za-z]{1,4}-?\\d{3,8}[A-Za-z]?(?![A-Za-z0-9])");
    private static final Pattern CODE_DIGIT_PREFIX_PATTERN = Pattern.compile("(?<![A-Za-z0-9])\\d[A-Za-z]{1,5}-?\\d{2,8}[A-Za-z]?(?![A-Za-z0-9])");
    private static final Pattern CODE_NUMERIC_PATTERN = Pattern.compile("(?<![A-Za-z0-9])\\d{5,8}(?![A-Za-z0-9])");
    private static final Pattern TAG_DIMENSION_PATTERN = Pattern.compile("(?i)^\\d{1,3}(?:\\.\\d+)?[xX]\\d{1,3}(?:\\.\\d+)?(?:[xX]\\d{1,3}(?:\\.\\d+)?)?(?:cm|mm)?$");
    private static final Pattern EIGHT_DIGIT_PATTERN = Pattern.compile("(?<!\\d)\\d{8}(?!\\d)");
    private static final Pattern SIZE_LABELED_PATTERN = Pattern.compile("(?i)(?:尺寸|size)\\s*[:：]?\\s*([0-9]{1,3}(?:\\.[0-9]+)?\\s*[xX×]\\s*[0-9]{1,3}(?:\\.[0-9]+)?(?:\\s*[xX×]\\s*[0-9]{1,3}(?:\\.[0-9]+)?)?\\s*(?:cm|mm|厘米|公分)?)");
    private static final Pattern SIZE_BARE_PATTERN = Pattern.compile("(?i)([0-9]{1,3}(?:\\.[0-9]+)?\\s*[xX×]\\s*[0-9]{1,3}(?:\\.[0-9]+)?(?:\\s*[xX×]\\s*[0-9]{1,3}(?:\\.[0-9]+)?)?\\s*(?:cm|mm|厘米|公分))");
    private static final String COMMODITY_VIEW_ENDPOINT = "https://www.szwego.com/commodity/view";
    private static final Pattern ITEM_STATE_MARKER_PATTERN = Pattern.compile("window\\.__INITIAL_STATE__\\s*=");
    private static final Pattern ITEM_NUXT_MARKER_PATTERN = Pattern.compile("window\\.__NUXT__\\s*=");
    private static final Map<String, String> BRAND_ALIAS_MAP = buildBrandAliasMap();
    private static final Set<String> GENERIC_EN_TAG_BLACKLIST = buildGenericEnTagBlacklist();

    private final WsAlbumConfigMapper wsAlbumConfigMapper;
    private final WsAlbumShopMapper wsAlbumShopMapper;
    private final WsAlbumShopTagMapper wsAlbumShopTagMapper;
    private final WsAlbumProductImportMapper wsAlbumProductImportMapper;
    private final WsAlbumProductImportMediaMapper wsAlbumProductImportMediaMapper;
    private final WsAlbumProductImportTagRelMapper wsAlbumProductImportTagRelMapper;
    private final WsAlbumCrawlLogMapper wsAlbumCrawlLogMapper;
    private final WsAlbumCrawlRequestLogMapper wsAlbumCrawlRequestLogMapper;
    private final WsAlbumImportLogMapper wsAlbumImportLogMapper;
    private final WsAlbumMediaMapMapper wsAlbumMediaMapMapper;
    private final ProductMapper productMapper;
    private final ProductImageMapper productImageMapper;
    private final ProductI18nMapper productI18nMapper;
    private final CategoryMapper categoryMapper;
    private final LlmService llmService;
    private final OssConfigService ossConfigService;
    private final ObjectMapper objectMapper;
    private final OssProperties ossProperties;
    private final WsAlbumProperties wsAlbumProperties;
    private final JdbcTemplate jdbcTemplate;

    @Qualifier("wsAlbumCrawlExecutor")
    private final ThreadPoolTaskExecutor wsAlbumCrawlExecutor;

    @Qualifier("wsAlbumImportExecutor")
    private final ThreadPoolTaskExecutor wsAlbumImportExecutor;

    private final TransactionTemplate transactionTemplate;

    private final Map<String, String> shopRunningBatchMap = new ConcurrentHashMap<String, String>();
    private final Map<String, String> crawlBatchShopMap = new ConcurrentHashMap<String, String>();
    private final Map<String, Boolean> crawlStopFlagMap = new ConcurrentHashMap<String, Boolean>();
    private final Map<String, Future<?>> crawlFutureMap = new ConcurrentHashMap<String, Future<?>>();
    private final Map<String, Future<?>> importFutureMap = new ConcurrentHashMap<String, Future<?>>();

    private volatile boolean ossBucketChecked = false;
    private volatile String resolvedOssEndpoint;
    private volatile String ossConfigSignature;

    @PostConstruct
    public void recoverInterruptedCrawlTasks() {
        ensureImportTableCompatibility();
        ensureShopTableCompatibility();
        recoverInterruptedImportTasks();
        List<WsAlbumCrawlLog> runningLogs = wsAlbumCrawlLogMapper.selectList(new LambdaQueryWrapper<WsAlbumCrawlLog>()
                .eq(WsAlbumCrawlLog::getStatus, CRAWL_STATUS_RUNNING));
        if (runningLogs == null || runningLogs.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (WsAlbumCrawlLog log : runningLogs) {
            log.setStatus(CRAWL_STATUS_FAILED);
            if (StrUtil.isBlank(log.getErrorMessage())) {
                log.setErrorMessage("任务已中断（服务重启或异常退出），请重试");
            }
            log.setEndTime(now);
            wsAlbumCrawlLogMapper.updateById(log);
        }
    }

    private void ensureImportTableCompatibility() {
        ensureImportColumnAsText("title");
        ensureImportColumnAsText("sub_title");
    }

    private void ensureShopTableCompatibility() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ws_album_shop' AND COLUMN_NAME = 'price_detect_config_json'",
                    Integer.class
            );
            if (count != null && count > 0) {
                return;
            }
            jdbcTemplate.execute("ALTER TABLE ws_album_shop ADD COLUMN price_detect_config_json LONGTEXT");
        } catch (Exception ignored) {
            // 不阻断服务启动，若缺列会在接口层反馈SQL异常。
        }
    }

    private void ensureImportColumnAsText(String columnName) {
        try {
            List<String> definitions = jdbcTemplate.queryForList(
                    "SELECT COLUMN_TYPE FROM information_schema.COLUMNS " +
                            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ws_album_product_import' AND COLUMN_NAME = ?",
                    String.class,
                    columnName
            );
            if (definitions == null || definitions.isEmpty()) {
                return;
            }
            String columnType = definitions.get(0);
            if (columnType == null) {
                return;
            }
            String normalized = columnType.toLowerCase(Locale.ROOT);
            if ("text".equals(normalized) || "longtext".equals(normalized) || "mediumtext".equals(normalized)) {
                return;
            }
            jdbcTemplate.execute("ALTER TABLE ws_album_product_import MODIFY COLUMN " + columnName + " TEXT DEFAULT NULL");
        } catch (Exception ignored) {
            // 不阻断服务启动，抓取流程有字段截断兜底。
        }
    }

    private void recoverInterruptedImportTasks() {
        List<WsAlbumImportLog> runningLogs = wsAlbumImportLogMapper.selectList(new LambdaQueryWrapper<WsAlbumImportLog>()
                .eq(WsAlbumImportLog::getStatus, IMPORT_LOG_STATUS_RUNNING));
        if (runningLogs == null || runningLogs.isEmpty()) {
            return;
        }
        for (WsAlbumImportLog log : runningLogs) {
            log.setStatus(IMPORT_LOG_STATUS_FAILED);
            if (StrUtil.isBlank(log.getErrorMessage())) {
                log.setErrorMessage("任务已中断（服务重启或异常退出），请重试");
            }
            wsAlbumImportLogMapper.updateById(log);
        }
    }

    private void recoverOrphanRunningTasks() {
        List<WsAlbumCrawlLog> runningLogs = wsAlbumCrawlLogMapper.selectList(new LambdaQueryWrapper<WsAlbumCrawlLog>()
                .eq(WsAlbumCrawlLog::getStatus, CRAWL_STATUS_RUNNING)
                .orderByDesc(WsAlbumCrawlLog::getId)
                .last("limit 200"));
        if (runningLogs == null || runningLogs.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (WsAlbumCrawlLog log : runningLogs) {
            String shopId = log.getShopId();
            String batchNo = log.getCrawlBatchNo();
            Future<?> activeFuture = StrUtil.isBlank(batchNo) ? null : crawlFutureMap.get(batchNo);
            if (activeFuture != null && !activeFuture.isDone() && !activeFuture.isCancelled()) {
                continue;
            }
            log.setStatus(CRAWL_STATUS_FAILED);
            if (StrUtil.isBlank(log.getErrorMessage())) {
                log.setErrorMessage("任务未检测到活动执行线程，已自动结束，请重试");
            }
            if (log.getEndTime() == null) {
                log.setEndTime(now);
            }
            wsAlbumCrawlLogMapper.updateById(log);
            if (StrUtil.isNotBlank(shopId) && StrUtil.isNotBlank(batchNo)) {
                shopRunningBatchMap.remove(shopId, batchNo);
                crawlBatchShopMap.remove(batchNo, shopId);
                crawlFutureMap.remove(batchNo);
                crawlStopFlagMap.remove(batchNo);
            }
        }
    }

    @Override
    public WsAlbumConfigVO getConfig() {
        WsAlbumConfig entity = getEnabledConfig();
        if (entity == null) {
            return null;
        }
        return toConfigVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WsAlbumConfigVO saveConfig(WsAlbumConfigSaveRequest request) {
        WsAlbumConfig entity;
        if (request.getId() != null) {
            entity = wsAlbumConfigMapper.selectById(request.getId());
            if (entity == null) {
                throw new BusinessException("抓取配置不存在");
            }
        } else {
            entity = new WsAlbumConfig();
        }

        entity.setConfigName(StrUtil.blankToDefault(request.getConfigName(), "默认配置"));
        entity.setTokenEnc(WsAlbumTokenCryptoUtil.encrypt(request.getToken().trim(), wsAlbumProperties.getTokenSecret()));
        entity.setEnabled(request.getEnabled() == null ? 1 : request.getEnabled());
        entity.setDefaultTransLang(StrUtil.blankToDefault(request.getDefaultTransLang(), "en"));
        entity.setDefaultXWgLang(StrUtil.blankToDefault(request.getDefaultXWgLang(), "zh"));
        entity.setDefaultMaxPages(request.getDefaultMaxPages() == null ? 20 : Math.max(1, request.getDefaultMaxPages()));
        entity.setRemark(request.getRemark());

        if (entity.getId() == null) {
            wsAlbumConfigMapper.insert(entity);
        } else {
            wsAlbumConfigMapper.updateById(entity);
        }

        if (entity.getEnabled() != null && entity.getEnabled() == 1) {
            wsAlbumConfigMapper.update(null, new LambdaUpdateWrapper<WsAlbumConfig>()
                    .set(WsAlbumConfig::getEnabled, 0)
                    .ne(WsAlbumConfig::getId, entity.getId()));
        }
        return toConfigVO(wsAlbumConfigMapper.selectById(entity.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String testConfig(WsAlbumConfigTestRequest request) {
        WsAlbumConfig config = requireEnabledConfig();
        String token = decryptToken(config);

        CrawlRuntime runtime = new CrawlRuntime();
        runtime.albumId = request.getAlbumId();
        runtime.searchValue = "";
        runtime.startDate = "";
        runtime.endDate = "";
        runtime.tagGroupId = "";
        runtime.transLang = StrUtil.blankToDefault(config.getDefaultTransLang(), "en");
        runtime.xWgLanguage = StrUtil.blankToDefault(config.getDefaultXWgLang(), "zh");
        runtime.tagList = new ArrayList<Long>();

        JsonNode result = fetchAlbumPage(token, runtime, null);
        JsonNode targetAlbum = result.path("targetAlbum");
        String shopName = textValue(targetAlbum, "name");

        config.setLastVerifyTime(LocalDateTime.now());
        wsAlbumConfigMapper.updateById(config);

        int itemCount = result.path("items").isArray() ? result.path("items").size() : 0;
        return "连接成功: " + (StrUtil.isBlank(shopName) ? runtime.albumId : shopName) + "，首屏商品数=" + itemCount;
    }

    @Override
    public PageResponse<WsAlbumShopVO> listShops(Long page, Long size, String keyword, Integer status) {
        Page<WsAlbumShop> pageQuery = new Page<WsAlbumShop>(page, size);
        LambdaQueryWrapper<WsAlbumShop> wrapper = new LambdaQueryWrapper<WsAlbumShop>()
                .orderByDesc(WsAlbumShop::getUpdateTime);
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.and(w -> w.like(WsAlbumShop::getShopName, keyword)
                    .or().like(WsAlbumShop::getShopId, keyword)
                    .or().like(WsAlbumShop::getAlbumId, keyword));
        }
        if (status != null) {
            wrapper.eq(WsAlbumShop::getStatus, status);
        }
        Page<WsAlbumShop> result = wsAlbumShopMapper.selectPage(pageQuery, wrapper);
        Page<WsAlbumShopVO> voPage = new Page<WsAlbumShopVO>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setPages(result.getPages());
        voPage.setRecords(result.getRecords().stream().map(item -> {
            WsAlbumShopVO vo = new WsAlbumShopVO();
            BeanUtils.copyProperties(item, vo);
            return vo;
        }).collect(Collectors.toList()));
        return PageUtil.toPageResponse(voPage);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WsAlbumShop addShop(WsAlbumShopAddRequest request) {
        WsAlbumConfig config = requireEnabledConfig();
        String token = decryptToken(config);

        CrawlRuntime runtime = new CrawlRuntime();
        runtime.albumId = request.getAlbumId().trim();
        runtime.searchValue = "";
        runtime.startDate = "";
        runtime.endDate = "";
        runtime.tagGroupId = "";
        runtime.transLang = StrUtil.blankToDefault(config.getDefaultTransLang(), "en");
        runtime.xWgLanguage = StrUtil.blankToDefault(config.getDefaultXWgLang(), "zh");
        runtime.tagList = new ArrayList<Long>();

        JsonNode result = fetchAlbumPage(token, runtime, null);
        WsAlbumShop shop = upsertShopFromResult(result, runtime.albumId, request.getRemark());
        config.setLastSuccessTime(LocalDateTime.now());
        wsAlbumConfigMapper.updateById(config);
        return shop;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WsAlbumShop updateShop(WsAlbumShopUpdateRequest request) {
        WsAlbumShop shop = wsAlbumShopMapper.selectOne(new LambdaQueryWrapper<WsAlbumShop>()
                .eq(WsAlbumShop::getShopId, request.getShopId()));
        if (shop == null) {
            throw new BusinessException("店铺不存在");
        }
        if (request.getRemark() != null) {
            shop.setRemark(request.getRemark());
        }
        if (request.getStatus() != null) {
            shop.setStatus(request.getStatus());
        }
        if (request.getPriceDetectConfigJson() != null) {
            shop.setPriceDetectConfigJson(normalizePriceDetectConfigJson(request.getPriceDetectConfigJson()));
        }
        wsAlbumShopMapper.updateById(shop);
        return shop;
    }

    @Override
    public WsAlbumShop detailShop(String shopId) {
        WsAlbumShop shop = wsAlbumShopMapper.selectOne(new LambdaQueryWrapper<WsAlbumShop>()
                .eq(WsAlbumShop::getShopId, shopId));
        if (shop == null) {
            throw new BusinessException("店铺不存在");
        }
        return shop;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WsAlbumShop refreshShop(WsAlbumShopRefreshRequest request) {
        WsAlbumConfig config = requireEnabledConfig();
        String token = decryptToken(config);

        String albumId = null;
        String remark = null;
        if (StrUtil.isNotBlank(request.getShopId())) {
            WsAlbumShop existing = wsAlbumShopMapper.selectOne(new LambdaQueryWrapper<WsAlbumShop>()
                    .eq(WsAlbumShop::getShopId, request.getShopId()));
            if (existing == null) {
                throw new BusinessException("店铺不存在");
            }
            albumId = StrUtil.blankToDefault(existing.getAlbumId(), existing.getShopId());
            remark = existing.getRemark();
        }
        if (StrUtil.isBlank(albumId)) {
            albumId = request.getAlbumId();
        }
        if (StrUtil.isBlank(albumId)) {
            throw new BusinessException("请提供shopId或albumId");
        }

        CrawlRuntime runtime = new CrawlRuntime();
        runtime.albumId = albumId;
        runtime.searchValue = "";
        runtime.startDate = "";
        runtime.endDate = "";
        runtime.tagGroupId = "";
        runtime.transLang = StrUtil.blankToDefault(config.getDefaultTransLang(), "en");
        runtime.xWgLanguage = StrUtil.blankToDefault(config.getDefaultXWgLang(), "zh");
        runtime.tagList = new ArrayList<Long>();

        JsonNode result = fetchAlbumPage(token, runtime, null);
        WsAlbumShop shop = upsertShopFromResult(result, albumId, remark);
        config.setLastSuccessTime(LocalDateTime.now());
        wsAlbumConfigMapper.updateById(config);
        return shop;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WsAlbumCrawlStartVO startCrawl(WsAlbumCrawlStartRequest request, String operatorName) {
        WsAlbumConfig config = requireEnabledConfig();
        String token = decryptToken(config);

        CrawlRuntime runtime = buildCrawlRuntime(request, config);
        WsAlbumShop shop = resolveTargetShop(runtime);

        if (shop.getStatus() != null && shop.getStatus() == 0) {
            throw new BusinessException("该店铺已停用，无法抓取");
        }

        runtime.shopId = shop.getShopId();
        runtime.shopName = shop.getShopName();
        if ("INCREMENTAL".equals(runtime.crawlMode) && runtime.pageTimestampStart == null
                && normalizedTagCount(runtime.tagList) <= 1) {
            runtime.pageTimestampStart = loadLatestIncrementalTimestamp(runtime);
        }

        if (!tryAcquireShopLock(runtime.shopId)) {
            throw new BusinessException("该店铺已有抓取任务在执行，请稍后再试");
        }

        final String lockShopKey = runtime.shopId;
        String batchNo = null;
        try {
            batchNo = buildBatchNo("C");
            shopRunningBatchMap.put(runtime.shopId, batchNo);
            crawlBatchShopMap.put(batchNo, runtime.shopId);
            crawlStopFlagMap.remove(batchNo);

            WsAlbumCrawlLog log = new WsAlbumCrawlLog();
            log.setCrawlBatchNo(batchNo);
            log.setShopId(runtime.shopId);
            log.setShopName(runtime.shopName);
            log.setCrawlMode(runtime.crawlMode);
            log.setRequestParamsJson(toJson(runtime.toRequestParamsMap()));
            log.setStartDate(runtime.startDate);
            log.setEndDate(runtime.endDate);
            log.setTagListJson(toJson(runtime.tagList));
            log.setTagGroupId(runtime.tagGroupId);
            log.setKeyword(runtime.searchValue);
            log.setPageTimestampStart(runtime.pageTimestampStart);
            log.setPageTimestampEnd(runtime.pageTimestampStart);
            log.setPageCount(0);
            log.setFetchedCount(0);
            log.setInsertedCount(0);
            log.setUpdatedCount(0);
            log.setDuplicateCount(0);
            log.setFailedCount(0);
            log.setStatus(CRAWL_STATUS_RUNNING);
            log.setOperatorId(operatorName);
            log.setOperatorName(operatorName);
            log.setStartTime(LocalDateTime.now());
            wsAlbumCrawlLogMapper.insert(log);

            final Long logId = log.getId();
            final String runtimeBatchNo = batchNo;
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        submitCrawlTask(logId, runtimeBatchNo, runtime, token, config.getId(), lockShopKey);
                    }

                    @Override
                    public void afterCompletion(int status) {
                        if (status != TransactionSynchronization.STATUS_COMMITTED) {
                            releaseShopLock(lockShopKey, runtimeBatchNo);
                        }
                    }
                });
            } else {
                submitCrawlTask(logId, runtimeBatchNo, runtime, token, config.getId(), lockShopKey);
            }

            WsAlbumCrawlStartVO vo = new WsAlbumCrawlStartVO();
            vo.setCrawlBatchNo(batchNo);
            vo.setStatus("running");
            return vo;
        } catch (Exception ex) {
            releaseShopLock(lockShopKey, batchNo);
            throw ex;
        }
    }

    private boolean tryAcquireShopLock(String shopId) {
        String previous = shopRunningBatchMap.putIfAbsent(shopId, "LOCKED");
        if (previous == null) {
            return true;
        }
        if (isStaleShopLock(shopId, previous)) {
            boolean removed = shopRunningBatchMap.remove(shopId, previous);
            if (removed) {
                previous = shopRunningBatchMap.putIfAbsent(shopId, "LOCKED");
                return previous == null;
            }
        }
        return false;
    }

    private boolean isStaleShopLock(String shopId, String currentLockValue) {
        if (StrUtil.isBlank(shopId)) {
            return true;
        }
        if (StrUtil.isBlank(currentLockValue) || "LOCKED".equals(currentLockValue)) {
            Long runningCount = wsAlbumCrawlLogMapper.selectCount(new LambdaQueryWrapper<WsAlbumCrawlLog>()
                    .eq(WsAlbumCrawlLog::getShopId, shopId)
                    .eq(WsAlbumCrawlLog::getStatus, CRAWL_STATUS_RUNNING));
            return runningCount == null || runningCount <= 0;
        }
        Future<?> future = crawlFutureMap.get(currentLockValue);
        if (future != null && !future.isDone() && !future.isCancelled()) {
            return false;
        }
        WsAlbumCrawlLog running = wsAlbumCrawlLogMapper.selectOne(new LambdaQueryWrapper<WsAlbumCrawlLog>()
                .eq(WsAlbumCrawlLog::getCrawlBatchNo, currentLockValue)
                .last("limit 1"));
        return running == null || running.getStatus() == null || running.getStatus() != CRAWL_STATUS_RUNNING;
    }

    @Override
    public WsAlbumCrawlStartVO retryCrawl(WsAlbumCrawlRetryRequest request, String operatorName) {
        WsAlbumCrawlLog log = wsAlbumCrawlLogMapper.selectOne(new LambdaQueryWrapper<WsAlbumCrawlLog>()
                .eq(WsAlbumCrawlLog::getCrawlBatchNo, request.getCrawlBatchNo()));
        if (log == null) {
            throw new BusinessException("抓取批次不存在");
        }
        Map<String, Object> map;
        try {
            map = objectMapper.readValue(log.getRequestParamsJson(), Map.class);
        } catch (Exception e) {
            throw new BusinessException("历史抓取参数解析失败: " + e.getMessage());
        }

        WsAlbumCrawlStartRequest retryRequest = new WsAlbumCrawlStartRequest();
        retryRequest.setShopId(stringValue(map.get("shopId")));
        retryRequest.setAlbumId(stringValue(map.get("albumId")));
        retryRequest.setCrawlMode(stringValue(map.get("crawlMode")));
        retryRequest.setStartDate(stringValue(map.get("startDate")));
        retryRequest.setEndDate(stringValue(map.get("endDate")));
        retryRequest.setTagGroupId(stringValue(map.get("tagGroupId")));
        retryRequest.setSearchValue(stringValue(map.get("searchValue")));
        retryRequest.setStartTimestamp(longValue(map.get("startTimestamp")));
        retryRequest.setMaxPages(intValue(map.get("maxPages")));
        retryRequest.setEnableTimestampUpdate(intValue(map.get("enableTimestampUpdate")));
        retryRequest.setMergeItemIds(intValue(map.get("mergeItemIds")));
        Object tagList = map.get("tagList");
        if (tagList instanceof List) {
            List<Long> ids = new ArrayList<Long>();
            for (Object obj : (List<?>) tagList) {
                if (obj != null) {
                    ids.add(Long.valueOf(String.valueOf(obj)));
                }
            }
            retryRequest.setTagList(ids);
        }
        Object itemIds = map.get("itemIds");
        if (itemIds instanceof List) {
            List<String> ids = new ArrayList<String>();
            for (Object obj : (List<?>) itemIds) {
                if (obj != null) {
                    ids.add(String.valueOf(obj));
                }
            }
            retryRequest.setItemIds(ids);
        }
        return startCrawl(retryRequest, operatorName);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WsAlbumCrawlStartVO stopCrawl(WsAlbumCrawlStopRequest request, String operatorName) {
        String batchNo = StrUtil.trim(request.getCrawlBatchNo());
        WsAlbumCrawlLog log = wsAlbumCrawlLogMapper.selectOne(new LambdaQueryWrapper<WsAlbumCrawlLog>()
                .eq(WsAlbumCrawlLog::getCrawlBatchNo, batchNo)
                .last("limit 1"));
        if (log == null) {
            throw new BusinessException("抓取批次不存在");
        }
        if (log.getStatus() == null || log.getStatus() != CRAWL_STATUS_RUNNING) {
            throw new BusinessException("仅执行中的任务可停止");
        }

        crawlStopFlagMap.put(batchNo, Boolean.TRUE);
        Future<?> future = crawlFutureMap.get(batchNo);
        if (future != null) {
            future.cancel(true);
        }
        String activeShopId = crawlBatchShopMap.get(batchNo);
        if (StrUtil.isNotBlank(activeShopId)) {
            shopRunningBatchMap.remove(activeShopId, batchNo);
        }
        log.setStatus(CRAWL_STATUS_STOPPED);
        log.setEndTime(LocalDateTime.now());
        log.setErrorMessage("任务已手动停止");
        wsAlbumCrawlLogMapper.updateById(log);

        WsAlbumCrawlStartVO vo = new WsAlbumCrawlStartVO();
        vo.setCrawlBatchNo(batchNo);
        vo.setStatus("stopping");
        return vo;
    }

    @Override
    public PageResponse<WsAlbumCrawlLogVO> listCrawlLogs(Long page, Long size, String shopId, Integer status, String crawlMode) {
        recoverOrphanRunningTasks();
        Page<WsAlbumCrawlLog> query = new Page<WsAlbumCrawlLog>(page, size);
        LambdaQueryWrapper<WsAlbumCrawlLog> wrapper = new LambdaQueryWrapper<WsAlbumCrawlLog>()
                // Sort by generated id first to avoid mixed-timezone createTime causing
                // newly created tasks to fall behind older records.
                .orderByDesc(WsAlbumCrawlLog::getId)
                .orderByDesc(WsAlbumCrawlLog::getCreateTime);
        if (StrUtil.isNotBlank(shopId)) {
            wrapper.eq(WsAlbumCrawlLog::getShopId, shopId);
        }
        if (status != null) {
            wrapper.eq(WsAlbumCrawlLog::getStatus, status);
        }
        if (StrUtil.isNotBlank(crawlMode)) {
            wrapper.eq(WsAlbumCrawlLog::getCrawlMode, crawlMode);
        }
        Page<WsAlbumCrawlLog> result = wsAlbumCrawlLogMapper.selectPage(query, wrapper);
        Page<WsAlbumCrawlLogVO> voPage = new Page<WsAlbumCrawlLogVO>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setPages(result.getPages());
        voPage.setRecords(result.getRecords().stream().map(item -> {
            WsAlbumCrawlLogVO vo = new WsAlbumCrawlLogVO();
            BeanUtils.copyProperties(item, vo);
            return vo;
        }).collect(Collectors.toList()));
        return PageUtil.toPageResponse(voPage);
    }

    @Override
    public WsAlbumCrawlLogVO crawlLogDetail(String crawlBatchNo) {
        recoverOrphanRunningTasks();
        WsAlbumCrawlLog log = wsAlbumCrawlLogMapper.selectOne(new LambdaQueryWrapper<WsAlbumCrawlLog>()
                .eq(WsAlbumCrawlLog::getCrawlBatchNo, crawlBatchNo));
        if (log == null) {
            throw new BusinessException("抓取日志不存在");
        }
        WsAlbumCrawlLogVO vo = new WsAlbumCrawlLogVO();
        BeanUtils.copyProperties(log, vo);
        return vo;
    }

    @Override
    public List<WsAlbumCrawlRequestLogVO> listCrawlRequestLogs(String crawlBatchNo, Integer limit) {
        int queryLimit = limit == null ? 200 : Math.max(1, Math.min(2000, limit));
        List<WsAlbumCrawlRequestLog> rows = wsAlbumCrawlRequestLogMapper.selectList(new LambdaQueryWrapper<WsAlbumCrawlRequestLog>()
                .eq(WsAlbumCrawlRequestLog::getCrawlBatchNo, crawlBatchNo)
                .orderByAsc(WsAlbumCrawlRequestLog::getPageNo)
                .orderByAsc(WsAlbumCrawlRequestLog::getId)
                .last("limit " + queryLimit));
        return rows.stream().map(item -> {
            WsAlbumCrawlRequestLogVO vo = new WsAlbumCrawlRequestLogVO();
            BeanUtils.copyProperties(item, vo);
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public PageResponse<WsAlbumProductImportVO> listImportProducts(Long page,
                                                                    Long size,
                                                                    String shopId,
                                                                    String tagGroupId,
                                                                    String crawlBatchNo,
                                                                    Integer importStatus,
                                                                    String keyword,
                                                                    String goodsId,
                                                                    Integer hasPrice,
                                                                    Integer hasVideo,
                                                                    Integer isAbnormal,
                                                                    String tagName) {
        Page<WsAlbumProductImport> query = new Page<WsAlbumProductImport>(page, size);
        LambdaQueryWrapper<WsAlbumProductImport> wrapper = new LambdaQueryWrapper<WsAlbumProductImport>()
                .eq(WsAlbumProductImport::getDeleted, 0);

        if (StrUtil.isNotBlank(shopId)) {
            wrapper.eq(WsAlbumProductImport::getShopId, shopId);
        }
        if (StrUtil.isNotBlank(tagGroupId)) {
            wrapper.eq(WsAlbumProductImport::getTagGroupId, tagGroupId);
        }
        if (StrUtil.isNotBlank(crawlBatchNo)) {
            wrapper.eq(WsAlbumProductImport::getCrawlBatchNo, crawlBatchNo);
        }
        if (importStatus != null) {
            wrapper.eq(WsAlbumProductImport::getImportStatus, importStatus);
        }
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.and(w -> w.like(WsAlbumProductImport::getTitle, keyword)
                    .or().like(WsAlbumProductImport::getSubTitle, keyword)
                    .or().like(WsAlbumProductImport::getShopName, keyword));
        }
        if (StrUtil.isNotBlank(goodsId)) {
            wrapper.eq(WsAlbumProductImport::getGoodsId, goodsId);
        }
        if (hasPrice != null) {
            if (hasPrice == 1) {
                wrapper.isNotNull(WsAlbumProductImport::getItemPrice).ne(WsAlbumProductImport::getItemPrice, "");
            } else {
                wrapper.and(w -> w.isNull(WsAlbumProductImport::getItemPrice).or().eq(WsAlbumProductImport::getItemPrice, ""));
            }
        }
        if (hasVideo != null) {
            wrapper.eq(WsAlbumProductImport::getHasVideo, hasVideo);
        }
        if (isAbnormal != null) {
            wrapper.eq(WsAlbumProductImport::getIsAbnormal, isAbnormal);
        }

        if (StrUtil.isNotBlank(tagName)) {
            List<WsAlbumProductImportTagRel> matchedRels = wsAlbumProductImportTagRelMapper.selectList(new LambdaQueryWrapper<WsAlbumProductImportTagRel>()
                    .like(WsAlbumProductImportTagRel::getTagName, tagName));
            if (matchedRels.isEmpty()) {
                Page<WsAlbumProductImportVO> emptyPage = new Page<WsAlbumProductImportVO>(page, size, 0);
                emptyPage.setRecords(new ArrayList<WsAlbumProductImportVO>());
                return PageUtil.toPageResponse(emptyPage);
            }
            Set<String> keySet = matchedRels.stream().map(item -> buildGoodsKey(item.getShopId(), item.getGoodsId())).collect(Collectors.toSet());
            if (keySet.isEmpty()) {
                Page<WsAlbumProductImportVO> emptyPage = new Page<WsAlbumProductImportVO>(page, size, 0);
                emptyPage.setRecords(new ArrayList<WsAlbumProductImportVO>());
                return PageUtil.toPageResponse(emptyPage);
            }
            wrapper.and(w -> {
                for (String key : keySet) {
                    String[] parts = key.split("\\|", 2);
                    if (parts.length == 2) {
                        w.or(ww -> ww.eq(WsAlbumProductImport::getShopId, parts[0]).eq(WsAlbumProductImport::getGoodsId, parts[1]));
                    }
                }
            });
        }

        // Align with source feed order: latest source time first, keep stable
        // in-page sequence when timestamps are equal.
        wrapper.orderByDesc(WsAlbumProductImport::getSourceUpdateTime)
                .orderByDesc(WsAlbumProductImport::getNewSendTime)
                .orderByAsc(WsAlbumProductImport::getId);

        Page<WsAlbumProductImport> result = wsAlbumProductImportMapper.selectPage(query, wrapper);
        List<WsAlbumProductImport> records = result.getRecords();

        Set<String> keys = records.stream().map(item -> buildGoodsKey(item.getShopId(), item.getGoodsId())).collect(Collectors.toSet());
        Map<String, List<String>> tagMap = loadTagMap(keys);

        Page<WsAlbumProductImportVO> voPage = new Page<WsAlbumProductImportVO>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setPages(result.getPages());
        voPage.setRecords(records.stream().map(item -> {
            WsAlbumProductImportVO vo = new WsAlbumProductImportVO();
            BeanUtils.copyProperties(item, vo);
            vo.setSourceTags(tagMap.getOrDefault(buildGoodsKey(item.getShopId(), item.getGoodsId()), new ArrayList<String>()));
            return vo;
        }).collect(Collectors.toList()));
        return PageUtil.toPageResponse(voPage);
    }

    @Override
    public WsAlbumProductImportDetailVO importProductDetail(Long id) {
        WsAlbumProductImport entity = wsAlbumProductImportMapper.selectById(id);
        if (entity == null || entity.getDeleted() != null && entity.getDeleted() == 1) {
            throw new BusinessException("中间库商品不存在");
        }
        WsAlbumProductImportDetailVO vo = new WsAlbumProductImportDetailVO();
        BeanUtils.copyProperties(entity, vo);

        List<WsAlbumProductImportMedia> mediaList = wsAlbumProductImportMediaMapper.selectList(new LambdaQueryWrapper<WsAlbumProductImportMedia>()
                .eq(WsAlbumProductImportMedia::getShopId, entity.getShopId())
                .eq(WsAlbumProductImportMedia::getGoodsId, entity.getGoodsId())
                .orderByAsc(WsAlbumProductImportMedia::getSortNo));

        List<String> images = mediaList.stream()
                .filter(item -> "image".equals(item.getMediaType()))
                .map(WsAlbumProductImportMedia::getSourceUrl)
                .collect(Collectors.toList());
        vo.setImageUrls(images);

        List<WsAlbumProductImportTagRel> tags = wsAlbumProductImportTagRelMapper.selectList(new LambdaQueryWrapper<WsAlbumProductImportTagRel>()
                .eq(WsAlbumProductImportTagRel::getShopId, entity.getShopId())
                .eq(WsAlbumProductImportTagRel::getGoodsId, entity.getGoodsId()));
        vo.setSourceTags(tags.stream().map(WsAlbumProductImportTagRel::getTagName).collect(Collectors.toList()));

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateImportProduct(WsAlbumProductImportUpdateRequest request) {
        WsAlbumProductImport entity = wsAlbumProductImportMapper.selectById(request.getId());
        if (entity == null || entity.getDeleted() != null && entity.getDeleted() == 1) {
            throw new BusinessException("中间库商品不存在");
        }
        if (request.getTitle() != null) {
            entity.setTitle(request.getTitle());
        }
        if (request.getSubTitle() != null) {
            entity.setSubTitle(request.getSubTitle());
        }
        if (request.getItemPrice() != null) {
            entity.setItemPrice(request.getItemPrice());
        }
        if (request.getCurrencyCode() != null) {
            entity.setCurrencyCode(request.getCurrencyCode());
        }
        if (request.getTagGroupId() != null) {
            entity.setTagGroupId(request.getTagGroupId());
        }
        if (request.getAbnormalReason() != null) {
            entity.setAbnormalReason(request.getAbnormalReason());
        }
        wsAlbumProductImportMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markImportProductAbnormal(WsAlbumProductImportMarkAbnormalRequest request) {
        Set<Long> ids = new LinkedHashSet<Long>(request.getIds());
        if (ids.isEmpty()) {
            throw new BusinessException("请选择商品");
        }
        wsAlbumProductImportMapper.update(null, new LambdaUpdateWrapper<WsAlbumProductImport>()
                .set(WsAlbumProductImport::getIsAbnormal, request.getIsAbnormal() == null ? 1 : request.getIsAbnormal())
                .set(WsAlbumProductImport::getAbnormalReason, request.getAbnormalReason())
                .in(WsAlbumProductImport::getId, ids));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteImportProducts(WsAlbumProductImportDeleteRequest request) {
        Set<Long> ids = new LinkedHashSet<Long>(request.getIds());
        if (ids.isEmpty()) {
            throw new BusinessException("请选择商品");
        }
        wsAlbumProductImportMapper.update(null, new LambdaUpdateWrapper<WsAlbumProductImport>()
                .set(WsAlbumProductImport::getDeleted, 1)
                .in(WsAlbumProductImport::getId, ids));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WsAlbumImportFormalResultVO importFormal(WsAlbumImportFormalRequest request, String operatorName) {
        // Fail fast before creating async import task/log when OSS is unavailable.
        // Otherwise, a large import-all run can generate many avoidable failed rows.
        ensureOssReady();

        if (categoryMapper.selectById(request.getCategoryId()) == null) {
            throw new BusinessException("目标分类不存在");
        }

        boolean importAllMode = isImportAllMode(request);
        String importAllShopId = importAllMode ? StrUtil.trimToEmpty(request.getShopId()) : "";
        Set<Long> idSet = importAllMode ? new LinkedHashSet<Long>() : normalizeImportIds(request.getIds());
        if (!importAllMode && idSet.isEmpty()) {
            throw new BusinessException("请选择要导入的商品");
        }

        int selectedCount = importAllMode ? countImportAllCandidates(importAllShopId) : idSet.size();
        if (selectedCount <= 0) {
            throw new BusinessException(importAllMode ? "当前没有可导入的中间库商品" : "请选择要导入的商品");
        }

        String logShopId = null;
        if (importAllMode && StrUtil.isNotBlank(importAllShopId)) {
            logShopId = importAllShopId;
        } else if (!importAllMode && !idSet.isEmpty()) {
            WsAlbumProductImport sample = wsAlbumProductImportMapper.selectById(idSet.iterator().next());
            logShopId = sample == null ? null : sample.getShopId();
        }

        String importBatchNo = buildBatchNo("I");

        WsAlbumImportLog log = new WsAlbumImportLog();
        log.setImportBatchNo(importBatchNo);
        log.setOperatorId(operatorName);
        log.setOperatorName(operatorName);
        log.setShopId(logShopId);
        log.setSelectedCount(selectedCount);
        log.setSuccessCount(0);
        log.setFailedCount(0);
        log.setPriceStrategyType(StrUtil.blankToDefault(request.getPriceStrategyType(), "NONE"));
        log.setPriceStrategyValue(request.getPriceStrategyValue());
        log.setCategoryIdsJson("[" + request.getCategoryId() + "]");
        log.setTagIdsJson(toJson(request.getTargetTags()));
        log.setStatus(IMPORT_LOG_STATUS_RUNNING);
        wsAlbumImportLogMapper.insert(log);

        final Long logId = log.getId();
        final String batchNo = importBatchNo;
        final Set<Long> runtimeIds = new LinkedHashSet<Long>(idSet);
        final WsAlbumImportFormalRequest runtimeRequest = copyImportRequest(request);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submitImportTask(logId, batchNo, runtimeIds, runtimeRequest);
                }

                @Override
                public void afterCompletion(int status) {
                    if (status != TransactionSynchronization.STATUS_COMMITTED) {
                        markImportTaskScheduleFailed(logId, "导入任务调度失败: 事务未提交");
                    }
                }
            });
        } else {
            submitImportTask(logId, batchNo, runtimeIds, runtimeRequest);
        }

        WsAlbumImportFormalResultVO vo = new WsAlbumImportFormalResultVO();
        vo.setImportBatchNo(importBatchNo);
        vo.setSelectedCount(selectedCount);
        vo.setSuccessCount(0);
        vo.setFailedCount(0);
        vo.setSkippedCount(0);
        vo.setStatus("running");
        return vo;
    }

    @Override
    public PageResponse<WsAlbumImportLogVO> listImportLogs(Long page, Long size, String shopId, Integer status) {
        Page<WsAlbumImportLog> query = new Page<WsAlbumImportLog>(page, size);
        LambdaQueryWrapper<WsAlbumImportLog> wrapper = new LambdaQueryWrapper<WsAlbumImportLog>()
                .orderByDesc(WsAlbumImportLog::getCreateTime);
        if (StrUtil.isNotBlank(shopId)) {
            wrapper.eq(WsAlbumImportLog::getShopId, shopId);
        }
        if (status != null) {
            wrapper.eq(WsAlbumImportLog::getStatus, status);
        }
        Page<WsAlbumImportLog> result = wsAlbumImportLogMapper.selectPage(query, wrapper);

        Page<WsAlbumImportLogVO> voPage = new Page<WsAlbumImportLogVO>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setPages(result.getPages());
        voPage.setRecords(result.getRecords().stream().map(item -> {
            WsAlbumImportLogVO vo = new WsAlbumImportLogVO();
            BeanUtils.copyProperties(item, vo);
            return vo;
        }).collect(Collectors.toList()));
        return PageUtil.toPageResponse(voPage);
    }

    @Override
    public WsAlbumImportLogVO importLogDetail(String importBatchNo) {
        WsAlbumImportLog log = wsAlbumImportLogMapper.selectOne(new LambdaQueryWrapper<WsAlbumImportLog>()
                .eq(WsAlbumImportLog::getImportBatchNo, importBatchNo));
        if (log == null) {
            throw new BusinessException("导入日志不存在");
        }
        WsAlbumImportLogVO vo = new WsAlbumImportLogVO();
        BeanUtils.copyProperties(log, vo);
        return vo;
    }

    private WsAlbumImportFormalRequest copyImportRequest(WsAlbumImportFormalRequest request) {
        WsAlbumImportFormalRequest runtime = new WsAlbumImportFormalRequest();
        runtime.setCategoryId(request.getCategoryId());
        runtime.setImportAll(request.getImportAll());
        runtime.setShopId(request.getShopId());
        runtime.setTargetTags(request.getTargetTags() == null ? new ArrayList<String>() : new ArrayList<String>(request.getTargetTags()));
        runtime.setPriceStrategyType(request.getPriceStrategyType());
        runtime.setPriceStrategyValue(request.getPriceStrategyValue());
        runtime.setAllowRepeatImport(request.getAllowRepeatImport());
        runtime.setDefaultTitleTemplate(request.getDefaultTitleTemplate());
        runtime.setIds(new ArrayList<Long>());
        return runtime;
    }

    private void submitImportTask(final Long logId,
                                  final String importBatchNo,
                                  final Set<Long> idSet,
                                  final WsAlbumImportFormalRequest request) {
        try {
            Future<?> future = wsAlbumImportExecutor.submit(new Runnable() {
                @Override
                public void run() {
                    executeImportAsync(logId, importBatchNo, idSet, request);
                }
            });
            importFutureMap.put(importBatchNo, future);
        } catch (Exception ex) {
            markImportTaskScheduleFailed(logId, "导入任务调度失败: " + ex.getMessage());
            throw ex;
        }
    }

    private void markImportTaskScheduleFailed(Long logId, String message) {
        WsAlbumImportLog log = wsAlbumImportLogMapper.selectById(logId);
        if (log == null) {
            return;
        }
        log.setStatus(IMPORT_LOG_STATUS_FAILED);
        log.setErrorMessage(StrUtil.sub(message, 0, 1000));
        wsAlbumImportLogMapper.updateById(log);
    }

    private void executeImportAsync(Long logId,
                                    String importBatchNo,
                                    Set<Long> idSet,
                                    WsAlbumImportFormalRequest request) {
        Set<Long> workingIdSet = idSet == null ? new LinkedHashSet<Long>() : new LinkedHashSet<Long>(idSet);
        if (isImportAllMode(request)) {
            String importAllShopId = StrUtil.trimToEmpty(request.getShopId());
            workingIdSet = loadImportAllCandidateIds(importAllShopId);
            updateImportLogSelectedCount(logId, workingIdSet.size());
        }
        if (workingIdSet.isEmpty()) {
            WsAlbumImportLog emptyLog = wsAlbumImportLogMapper.selectById(logId);
            if (emptyLog != null) {
                emptyLog.setSelectedCount(0);
                emptyLog.setSuccessCount(0);
                emptyLog.setFailedCount(0);
                emptyLog.setStatus(IMPORT_LOG_STATUS_SUCCESS);
                emptyLog.setErrorMessage("无可导入商品");
                wsAlbumImportLogMapper.updateById(emptyLog);
            }
            importFutureMap.remove(importBatchNo);
            return;
        }

        int success = 0;
        int failed = 0;
        int skipped = 0;
        StringBuilder errorBuilder = new StringBuilder();
        Map<String, PriceDetectConfig> priceDetectConfigCache = new HashMap<String, PriceDetectConfig>();
        try {
            for (Long id : workingIdSet) {
                WsAlbumProductImport row = wsAlbumProductImportMapper.selectById(id);
                if (row == null || isTrue(row.getDeleted())) {
                    failed++;
                    appendError(errorBuilder, "ID=" + id + " 商品不存在或已删除");
                    updateImportLogProgress(logId, success, failed);
                    continue;
                }

                try {
                    if (row.getImportStatus() != null && row.getImportStatus() == IMPORT_STATUS_SUCCESS
                            && !isTrue(request.getAllowRepeatImport())) {
                        failed++;
                        appendError(errorBuilder, "goodsId=" + row.getGoodsId() + " 已导入，未勾选允许重复导入");
                        updateImportLogProgress(logId, success, failed);
                        continue;
                    }

                    if (shouldSkipImportRow(row)) {
                        skipped++;
                        transactionTemplate.execute(new org.springframework.transaction.support.TransactionCallbackWithoutResult() {
                            @Override
                            protected void doInTransactionWithoutResult(org.springframework.transaction.TransactionStatus status) {
                                row.setImportStatus(IMPORT_STATUS_IGNORED);
                                row.setAbnormalReason("图文内容不足，已略过（仅1张图片或仅文案）");
                                wsAlbumProductImportMapper.updateById(row);
                            }
                        });
                        appendError(errorBuilder, "goodsId=" + row.getGoodsId() + " 已略过: 仅1张图片或仅文案");
                        updateImportLogProgress(logId, success, failed);
                        continue;
                    }

                    final WsAlbumProductImport currentRow = row;
                    Long productId = transactionTemplate.execute(status -> {
                        Long importedProductId = importOneProduct(currentRow, request, priceDetectConfigCache);
                        currentRow.setImportStatus(IMPORT_STATUS_SUCCESS);
                        currentRow.setFormalProductId(importedProductId);
                        currentRow.setAbnormalReason(null);
                        wsAlbumProductImportMapper.updateById(currentRow);
                        return importedProductId;
                    });
                    if (productId == null) {
                        throw new BusinessException("导入失败: 返回产品ID为空");
                    }
                    success++;
                } catch (Exception ex) {
                    failed++;
                    String errMsg = normalizeErrorMessage(ex);
                    transactionTemplate.execute(new org.springframework.transaction.support.TransactionCallbackWithoutResult() {
                        @Override
                        protected void doInTransactionWithoutResult(org.springframework.transaction.TransactionStatus status) {
                            row.setImportStatus(IMPORT_STATUS_FAILED);
                            row.setAbnormalReason(StrUtil.sub(errMsg, 0, 240));
                            wsAlbumProductImportMapper.updateById(row);
                        }
                    });
                    appendError(errorBuilder, "goodsId=" + row.getGoodsId() + " 导入失败: " + errMsg);
                }
                updateImportLogProgress(logId, success, failed);
            }

            WsAlbumImportLog doneLog = wsAlbumImportLogMapper.selectById(logId);
            if (doneLog != null) {
                doneLog.setSuccessCount(success);
                doneLog.setFailedCount(failed);
                doneLog.setStatus(failed == 0 ? IMPORT_LOG_STATUS_SUCCESS : IMPORT_LOG_STATUS_FAILED);
                String finalError = StrUtil.sub(errorBuilder.toString(), 0, 1000);
                if (skipped > 0) {
                    finalError = StrUtil.sub(("skipped=" + skipped + (StrUtil.isBlank(finalError) ? "" : " | " + finalError)), 0, 1000);
                }
                doneLog.setErrorMessage(finalError);
                wsAlbumImportLogMapper.updateById(doneLog);
            }
        } catch (Exception ex) {
            WsAlbumImportLog failedLog = wsAlbumImportLogMapper.selectById(logId);
            if (failedLog != null) {
                failedLog.setSuccessCount(success);
                failedLog.setFailedCount(failed + 1);
                failedLog.setStatus(IMPORT_LOG_STATUS_FAILED);
                failedLog.setErrorMessage(StrUtil.sub("任务异常终止: " + normalizeErrorMessage(ex), 0, 1000));
                wsAlbumImportLogMapper.updateById(failedLog);
            }
        } finally {
            importFutureMap.remove(importBatchNo);
        }
    }

    private boolean isImportAllMode(WsAlbumImportFormalRequest request) {
        return request != null && Boolean.TRUE.equals(request.getImportAll());
    }

    private Set<Long> normalizeImportIds(List<Long> ids) {
        Set<Long> idSet = new LinkedHashSet<Long>();
        if (ids == null || ids.isEmpty()) {
            return idSet;
        }
        for (Long id : ids) {
            if (id != null) {
                idSet.add(id);
            }
        }
        return idSet;
    }

    private int countImportAllCandidates(String shopId) {
        LambdaQueryWrapper<WsAlbumProductImport> wrapper = new LambdaQueryWrapper<WsAlbumProductImport>()
                .eq(WsAlbumProductImport::getDeleted, 0)
                .in(WsAlbumProductImport::getImportStatus, IMPORT_STATUS_PENDING, IMPORT_STATUS_FAILED);
        if (StrUtil.isNotBlank(shopId)) {
            wrapper.eq(WsAlbumProductImport::getShopId, shopId);
        }
        Long count = wsAlbumProductImportMapper.selectCount(wrapper);
        return count == null ? 0 : count.intValue();
    }

    private Set<Long> loadImportAllCandidateIds(String shopId) {
        LambdaQueryWrapper<WsAlbumProductImport> wrapper = new LambdaQueryWrapper<WsAlbumProductImport>()
                .select(WsAlbumProductImport::getId)
                .eq(WsAlbumProductImport::getDeleted, 0)
                .in(WsAlbumProductImport::getImportStatus, IMPORT_STATUS_PENDING, IMPORT_STATUS_FAILED)
                .orderByAsc(WsAlbumProductImport::getId);
        if (StrUtil.isNotBlank(shopId)) {
            wrapper.eq(WsAlbumProductImport::getShopId, shopId);
        }
        List<Object> rawIds = wsAlbumProductImportMapper.selectObjs(wrapper);
        Set<Long> result = new LinkedHashSet<Long>();
        if (rawIds == null || rawIds.isEmpty()) {
            return result;
        }
        for (Object rawId : rawIds) {
            Long id = castToLong(rawId);
            if (id != null) {
                result.add(id);
            }
        }
        return result;
    }

    private Long castToLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (Exception ignore) {
            return null;
        }
    }

    private String normalizeErrorMessage(Throwable throwable) {
        if (throwable == null) {
            return "未知错误";
        }
        String msg = throwable.getMessage();
        if (throwable instanceof BusinessException) {
            return StrUtil.blankToDefault(msg, "业务异常");
        }
        if (throwable.getCause() != null && StrUtil.isNotBlank(throwable.getCause().getMessage())) {
            return throwable.getCause().getMessage();
        }
        return StrUtil.blankToDefault(msg, throwable.getClass().getSimpleName());
    }

    private void updateImportLogProgress(Long logId, int success, int failed) {
        WsAlbumImportLog progress = new WsAlbumImportLog();
        progress.setId(logId);
        progress.setSuccessCount(success);
        progress.setFailedCount(failed);
        progress.setStatus(IMPORT_LOG_STATUS_RUNNING);
        wsAlbumImportLogMapper.updateById(progress);
    }

    private void updateImportLogSelectedCount(Long logId, int selectedCount) {
        WsAlbumImportLog progress = new WsAlbumImportLog();
        progress.setId(logId);
        progress.setSelectedCount(selectedCount);
        wsAlbumImportLogMapper.updateById(progress);
    }

    private boolean shouldSkipImportRow(WsAlbumProductImport row) {
        if (row == null) {
            return true;
        }
        if (isTrue(row.getHasVideo())) {
            return false;
        }
        int mediaCount = row.getMediaCount() == null ? 0 : row.getMediaCount();
        return mediaCount <= 1;
    }

    private Long importOneProduct(WsAlbumProductImport row,
                                  WsAlbumImportFormalRequest request,
                                  Map<String, PriceDetectConfig> priceDetectConfigCache) {
        List<WsAlbumProductImportMedia> mediaList = wsAlbumProductImportMediaMapper.selectList(new LambdaQueryWrapper<WsAlbumProductImportMedia>()
                .eq(WsAlbumProductImportMedia::getShopId, row.getShopId())
                .eq(WsAlbumProductImportMedia::getGoodsId, row.getGoodsId())
                .orderByAsc(WsAlbumProductImportMedia::getSortNo));

        List<WsAlbumProductImportMedia> imageMedia = mediaList.stream()
                .filter(item -> "image".equals(item.getMediaType()))
                .collect(Collectors.toList());
        List<WsAlbumProductImportMedia> videoThumbMedia = mediaList.stream()
                .filter(item -> "video_thumb".equals(item.getMediaType()))
                .collect(Collectors.toList());

        Map<String, UploadResult> uploadMap = new HashMap<String, UploadResult>();
        List<String> imageUrls = new ArrayList<String>();
        List<String> imageSources = new ArrayList<String>();
        for (WsAlbumProductImportMedia media : imageMedia) {
            addCandidate(imageSources, media.getSourceUrl());
        }
        if (imageSources.isEmpty()) {
            List<String> fallbackSources = collectFallbackImageCandidates(row);
            for (String source : fallbackSources) {
                addCandidate(imageSources, source);
            }
        }
        if (imageSources.isEmpty()) {
            for (WsAlbumProductImportMedia media : videoThumbMedia) {
                addCandidate(imageSources, media.getSourceUrl());
            }
        }

        if (!imageSources.isEmpty()) {
            for (String source : imageSources) {
                UploadResult result = resolveSourceToOss(source, "image");
                uploadMap.put(source, result);
                imageUrls.add(result.ossUrl);
            }
        }

        if (imageUrls.isEmpty()) {
            throw new BusinessException("该商品无可用图片/视频缩略图，无法导入正式库");
        }

        UploadResult videoUpload = null;
        String videoSource = row.getVideoUrl();
        if (StrUtil.isNotBlank(videoSource)) {
            videoUpload = resolveSourceToOss(videoSource, "video");
            uploadMap.put(videoSource, videoUpload);
        }

        Map<String, UploadResult> finalUploadMap = uploadMap;
        mediaList.forEach(item -> {
            UploadResult res = finalUploadMap.get(item.getSourceUrl());
            if (res != null) {
                item.setOssKey(res.ossKey);
                wsAlbumProductImportMediaMapper.updateById(item);
            }
        });

        String rawTitle = resolveProductTitle(row, request);
        String rawDescription = resolveProductDescription(row);
        DetectContentSource detectSource = resolveDetectContentSource(row, rawTitle, rawDescription);
        PriceDetectConfig priceDetectConfig = resolvePriceDetectConfig(row.getShopId(), priceDetectConfigCache);
        NormalizedImportContent normalizedContent = normalizeImportContent(rawTitle,
                rawDescription,
                detectSource.detectTitle,
                detectSource.detectDescription,
                priceDetectConfig);
        String resolvedTitle = normalizedContent.title;
        String resolvedDescription = normalizedContent.description;
        Product product = new Product();
        product.setTitle(resolvedTitle);
        product.setDescription(resolvedDescription);
        product.setCategoryId(request.getCategoryId());
        product.setPrice(resolveProductPrice(row.getItemPrice(),
                request.getPriceStrategyType(),
                request.getPriceStrategyValue(),
                normalizedContent.detectedPrice,
                priceDetectConfig));
        product.setSku(buildReadableSku());
        product.setTagsJson(TagUtil.toBilingualJson(new ArrayList<String>(), new ArrayList<String>()));
        product.setCoverImage(imageUrls.get(0));
        product.setVideoUrl(videoUpload == null ? null : videoUpload.ossUrl);
        product.setImageCount(imageUrls.size());
        product.setStatus(1);
        product.setViews(0);
        productMapper.insert(product);

        for (int i = 0; i < imageUrls.size(); i++) {
            ProductImage image = new ProductImage();
            image.setProductId(product.getId());
            image.setImageUrl(imageUrls.get(i));
            image.setImageSizeKb(0L);
            image.setSort(i + 1);
            productImageMapper.insert(image);
        }

        LocalizedProductContent localizedContent = saveLocalizedContentByLlm(product.getId(),
                detectSource.detectTitle,
                detectSource.detectDescription,
                resolvedTitle,
                resolvedDescription,
                priceDetectConfig);
        product.setTagsJson(buildImportProductTagsJson(row, request, normalizedContent, localizedContent));
        productMapper.updateById(product);

        return product.getId();
    }

    private LocalizedProductContent saveLocalizedContentByLlm(Long productId,
                                                              String llmTitleSource,
                                                              String llmDescriptionSource,
                                                              String fallbackTitle,
                                                              String fallbackDescription,
                                                              PriceDetectConfig priceDetectConfig) {
        String normalizedTitle = normalizeTitleText(fallbackTitle);
        String normalizedDescription = normalizeDescriptionText(fallbackDescription);
        String llmTitle = normalizeTitleText(normalizeForLlmInput(llmTitleSource));
        String llmDescription = normalizeDescriptionText(normalizeForLlmInput(llmDescriptionSource));
        String extractedSize = extractCompleteSize(llmTitle, llmDescription);
        String llmDescriptionInput = resolveLlmDescriptionSource(llmTitle, llmDescription);
        boolean allowEmptyDescriptionForShortCaption = isShortCaption(llmTitle) && StrUtil.isBlank(llmDescription);
        LlmTranslationResult translated = llmService.summarizeAndTranslateBilingual(llmTitle, llmDescriptionInput);
        if (translated == null) {
            return new LocalizedProductContent(normalizedTitle, normalizedDescription, "", "");
        }

        String zhTitle = normalizeTitleText(stripPriceTokens(StrUtil.trimToEmpty(translated.getZhTitle()), priceDetectConfig));
        String zhDescription = normalizeDescriptionText(stripPriceTokens(StrUtil.trimToEmpty(translated.getZhDescription()), priceDetectConfig));
        if (StrUtil.isBlank(zhTitle)) {
            zhTitle = normalizedTitle;
        }
        if (StrUtil.isBlank(zhDescription)) {
            zhDescription = normalizedDescription;
        }
        if (shouldClearDescriptionByShortCaption(allowEmptyDescriptionForShortCaption, zhDescription)) {
            zhDescription = "";
        }
        zhDescription = ensureZhDescriptionContainsSize(zhDescription, extractedSize);
        updateLocalizedProductContent(productId, zhTitle, zhDescription);

        String enTitle = normalizeTitleText(stripPriceTokens(StrUtil.trimToEmpty(translated.getEnTitle()), priceDetectConfig));
        String enDescription = normalizeDescriptionText(stripPriceTokens(StrUtil.trimToEmpty(translated.getEnDescription()), priceDetectConfig));
        if (StrUtil.isBlank(enTitle)) {
            LlmTranslationResult fallback = llmService.summarizeAndTranslateToEnglish(llmTitle, llmDescriptionInput);
            if (fallback != null) {
                enTitle = normalizeTitleText(stripPriceTokens(StrUtil.trimToEmpty(fallback.getEnTitle()), priceDetectConfig));
                enDescription = normalizeDescriptionText(stripPriceTokens(StrUtil.trimToEmpty(fallback.getEnDescription()), priceDetectConfig));
            }
        }
        if (shouldClearDescriptionByShortCaption(allowEmptyDescriptionForShortCaption, enDescription)) {
            enDescription = "";
        }
        enDescription = ensureEnDescriptionContainsSize(enDescription, extractedSize);
        if (StrUtil.isBlank(enTitle) && StrUtil.isBlank(enDescription)) {
            return new LocalizedProductContent(zhTitle, zhDescription, "", "");
        }
        ProductI18n existing = productI18nMapper.selectOne(new LambdaQueryWrapper<ProductI18n>()
                .eq(ProductI18n::getProductId, productId)
                .eq(ProductI18n::getLangCode, LanguageUtil.EN_LANG)
                .last("limit 1"));
        if (existing == null) {
            ProductI18n row = new ProductI18n();
            row.setProductId(productId);
            row.setLangCode(LanguageUtil.EN_LANG);
            row.setTitle(enTitle);
            row.setDescription(enDescription);
            productI18nMapper.insert(row);
        } else {
            existing.setTitle(enTitle);
            existing.setDescription(enDescription);
            productI18nMapper.updateById(existing);
        }
        return new LocalizedProductContent(zhTitle, zhDescription, enTitle, enDescription);
    }

    private String buildImportProductTagsJson(WsAlbumProductImport row,
                                              WsAlbumImportFormalRequest request,
                                              NormalizedImportContent normalizedContent,
                                              LocalizedProductContent localizedContent) {
        LinkedHashSet<String> zhTags = new LinkedHashSet<String>();
        LinkedHashSet<String> enTags = new LinkedHashSet<String>();
        if (request != null && request.getTargetTags() != null) {
            for (String targetTag : request.getTargetTags()) {
                appendZhTag(zhTags, targetTag);
                appendEnglishTag(enTags, normalizeKnownEnglishTag(targetTag));
            }
        }

        String zhSource = joinTagSource(localizedContent == null ? null : localizedContent.zhTitle,
                localizedContent == null ? null : localizedContent.zhDescription);
        if (StrUtil.isBlank(zhSource)) {
            zhSource = joinTagSource(normalizedContent == null ? null : normalizedContent.title,
                    normalizedContent == null ? null : normalizedContent.description);
        }
        String enSource = joinTagSource(localizedContent == null ? null : localizedContent.enTitle,
                localizedContent == null ? null : localizedContent.enDescription);
        String rawSource = joinTagSource(row == null ? null : row.getTitle(), row == null ? null : row.getSubTitle());
        appendBrandTags(zhTags, enTags, joinTagSource(zhSource, enSource, rawSource));
        appendZhContentTags(zhTags, zhSource);
        appendEnglishContentTags(enTags, joinTagSource(enSource, rawSource));

        if (normalizedContent != null && normalizedContent.codeTags != null) {
            for (String codeTag : normalizedContent.codeTags) {
                appendZhTag(zhTags, codeTag);
                appendEnglishTag(enTags, codeTag);
            }
        }
        return TagUtil.toBilingualJson(new ArrayList<String>(zhTags), new ArrayList<String>(enTags));
    }

    private static Map<String, String> buildBrandAliasMap() {
        Map<String, String> map = new LinkedHashMap<String, String>();
        addBrandAliases(map, "Gucci", "Gucci", "GUCCI", "GU..CCI", "GU CCI", "古奇", "古驰");
        addBrandAliases(map, "Louis Vuitton", "Louis Vuitton", "LV", "L.V.", "路易威登", "老花");
        addBrandAliases(map, "Hermès", "Hermès", "Hermes", "Hermè", "HERMES", "爱马仕");
        addBrandAliases(map, "Salvatore Ferragamo", "Salvatore Ferragamo", "Ferragamo", "菲拉格慕", "菲拉格莫");
        addBrandAliases(map, "Chanel", "Chanel", "香奈儿");
        addBrandAliases(map, "Dior", "Dior", "迪奥");
        addBrandAliases(map, "Prada", "Prada", "普拉达");
        addBrandAliases(map, "Celine", "Celine", "Céline", "赛琳");
        addBrandAliases(map, "Fendi", "Fendi", "芬迪");
        addBrandAliases(map, "Bottega Veneta", "Bottega Veneta", "BV", "葆蝶家");
        addBrandAliases(map, "Burberry", "Burberry", "巴宝莉");
        addBrandAliases(map, "Versace", "Versace", "范思哲");
        addBrandAliases(map, "Miu Miu", "Miu Miu", "Miumiu", "缪缪");
        addBrandAliases(map, "Saint Laurent", "Saint Laurent", "YSL", "Yves Saint Laurent", "圣罗兰");
        return map;
    }

    private static void addBrandAliases(Map<String, String> map, String canonical, String... aliases) {
        for (String alias : aliases) {
            String key = normalizeBrandKey(alias);
            if (StrUtil.isNotBlank(key)) {
                map.put(key, canonical);
            }
        }
    }

    private static Set<String> buildGenericEnTagBlacklist() {
        Set<String> set = new HashSet<String>();
        Collections.addAll(set, "new", "top", "quality", "replica", "copy", "factory", "detail", "details",
                "photo", "photos", "picture", "pictures", "set", "collection", "hot", "sale", "goods");
        return set;
    }

    private void appendBrandTags(Set<String> zhTags, Set<String> enTags, String source) {
        String normalizedSource = normalizeBrandKey(source);
        if (StrUtil.isBlank(normalizedSource)) {
            return;
        }
        LinkedHashSet<String> brands = new LinkedHashSet<String>();
        for (Map.Entry<String, String> entry : BRAND_ALIAS_MAP.entrySet()) {
            if (normalizedSource.contains(entry.getKey())) {
                brands.add(entry.getValue());
            }
        }
        for (String brand : brands) {
            appendZhTag(zhTags, brand);
            appendEnglishTag(enTags, brand);
        }
    }

    private void appendZhContentTags(Set<String> tags, String source) {
        String normalized = Normalizer.normalize(StrUtil.trimToEmpty(source), Normalizer.Form.NFKC);
        if (StrUtil.isBlank(normalized)) {
            return;
        }
        if (normalized.contains("腰带") || normalized.contains("皮带")) {
            appendZhTag(tags, "腰带");
        }
        if (normalized.contains("男士") || normalized.contains("男款")) {
            appendZhTag(tags, "男士");
        }
        if (normalized.contains("女士") || normalized.contains("女款")) {
            appendZhTag(tags, "女士");
        }
        if (normalized.contains("真皮") || normalized.contains("牛皮") || normalized.toLowerCase(Locale.ROOT).contains("leather")) {
            appendZhTag(tags, "皮革");
        }
        if (normalized.contains("针扣") || normalized.contains("扣头") || normalized.toLowerCase(Locale.ROOT).contains("buckle")) {
            appendZhTag(tags, "扣头");
        }
        if (normalized.contains("双面") || normalized.toLowerCase(Locale.ROOT).contains("reversible")) {
            appendZhTag(tags, "双面");
        }
    }

    private void appendEnglishContentTags(Set<String> tags, String source) {
        String normalized = Normalizer.normalize(StrUtil.trimToEmpty(source), Normalizer.Form.NFKC);
        if (StrUtil.isBlank(normalized)) {
            return;
        }
        String lower = removeDiacritics(normalized).toLowerCase(Locale.ROOT);
        if (lower.contains("belt") || normalized.contains("腰带") || normalized.contains("皮带")) {
            appendEnglishTag(tags, "Belt");
        }
        if (lower.contains("men") || normalized.contains("男士") || normalized.contains("男款")) {
            appendEnglishTag(tags, "Men's");
        }
        if (lower.contains("women") || lower.contains("ladies") || normalized.contains("女士") || normalized.contains("女款")) {
            appendEnglishTag(tags, "Women's");
        }
        if (lower.contains("leather") || normalized.contains("真皮") || normalized.contains("牛皮")) {
            appendEnglishTag(tags, "Leather");
        }
        if (lower.contains("buckle") || normalized.contains("针扣") || normalized.contains("扣头")) {
            appendEnglishTag(tags, "Buckle");
        }
        if (lower.contains("reversible") || normalized.contains("双面")) {
            appendEnglishTag(tags, "Reversible");
        }
        if (lower.contains("kelly")) {
            appendEnglishTag(tags, "Kelly");
        }

    }

    private String joinTagSource(String... parts) {
        StringBuilder sb = new StringBuilder();
        if (parts != null) {
            for (String part : parts) {
                if (StrUtil.isBlank(part)) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(part);
            }
        }
        return sb.toString();
    }

    private void appendZhTag(Set<String> tags, String rawTag) {
        String tag = normalizeGenericTag(rawTag);
        if (StrUtil.isBlank(tag) || isLikelyDimensionTag(tag)) {
            return;
        }
        String knownEnglish = normalizeKnownEnglishTag(tag);
        if (StrUtil.isNotBlank(knownEnglish) && !containsCjk(knownEnglish)) {
            tags.add(knownEnglish);
            return;
        }
        tags.add(tag);
    }

    private void appendEnglishTag(Set<String> tags, String rawTag) {
        String tag = normalizeKnownEnglishTag(rawTag);
        if (!isUsefulEnglishTag(tag)) {
            return;
        }
        tags.add(tag);
    }

    private String normalizeKnownEnglishTag(String rawTag) {
        String tag = normalizeGenericTag(rawTag);
        if (StrUtil.isBlank(tag)) {
            return "";
        }
        String brand = BRAND_ALIAS_MAP.get(normalizeBrandKey(tag));
        if (StrUtil.isNotBlank(brand)) {
            return brand;
        }
        String normalized = Normalizer.normalize(tag, Normalizer.Form.NFKC).trim();
        String lower = removeDiacritics(normalized).toLowerCase(Locale.ROOT);
        if (containsCjk(normalized)) {
            if (normalized.contains("腰带") || normalized.contains("皮带")) {
                return "Belt";
            }
            if (normalized.contains("男士") || normalized.contains("男款")) {
                return "Men's";
            }
            if (normalized.contains("女士") || normalized.contains("女款")) {
                return "Women's";
            }
            if (normalized.contains("真皮") || normalized.contains("牛皮")) {
                return "Leather";
            }
            if (normalized.contains("针扣") || normalized.contains("扣头")) {
                return "Buckle";
            }
            if (normalized.contains("双面")) {
                return "Reversible";
            }
            return "";
        }
        if ("lv".equals(lower)) {
            return "Louis Vuitton";
        }
        if ("ysl".equals(lower)) {
            return "Saint Laurent";
        }
        if ("hermes".equals(lower) || "herme".equals(lower)) {
            return "Hermès";
        }
        if ("gucci".equals(lower)) {
            return "Gucci";
        }
        if ("ferragamo".equals(lower)) {
            return "Salvatore Ferragamo";
        }
        if ("belt".equals(lower) || "belts".equals(lower)) {
            return "Belt";
        }
        if ("leather".equals(lower)) {
            return "Leather";
        }
        if ("buckle".equals(lower) || "buckles".equals(lower)) {
            return "Buckle";
        }
        if ("reversible".equals(lower)) {
            return "Reversible";
        }
        if ("men".equals(lower) || "mens".equals(lower) || "men's".equals(lower)) {
            return "Men's";
        }
        if ("women".equals(lower) || "womens".equals(lower) || "women's".equals(lower)
                || "ladies".equals(lower) || "lady".equals(lower)) {
            return "Women's";
        }
        return titleCaseEnglishTag(normalized);
    }

    private boolean isUsefulEnglishTag(String tag) {
        if (StrUtil.isBlank(tag) || containsCjk(tag) || isLikelyDimensionTag(tag)) {
            return false;
        }
        String normalized = tag.trim();
        if (normalized.length() < 2 || normalized.length() > 40) {
            return false;
        }
        String lower = removeDiacritics(normalized).toLowerCase(Locale.ROOT);
        if (GENERIC_EN_TAG_BLACKLIST.contains(lower)) {
            return false;
        }
        if (lower.matches("\\d+") || lower.matches("20\\d{2}") || lower.matches("19\\d{2}")) {
            return false;
        }
        return normalized.matches(".*[A-Za-z].*");
    }

    private String normalizeGenericTag(String rawTag) {
        if (StrUtil.isBlank(rawTag)) {
            return "";
        }
        String normalized = Normalizer.normalize(rawTag, Normalizer.Form.NFKC).trim();
        normalized = normalized.replaceAll("^[\\p{Punct}\\s]+", "").replaceAll("[\\p{Punct}\\s]+$", "");
        normalized = normalized.replaceAll("\\s{2,}", " ");
        return normalized.trim();
    }

    private static String normalizeBrandKey(String text) {
        if (StrUtil.isBlank(text)) {
            return "";
        }
        String normalized = removeDiacritics(Normalizer.normalize(text, Normalizer.Form.NFKC)).toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || (ch >= '\u4e00' && ch <= '\u9fff')) {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    private static String removeDiacritics(String text) {
        if (text == null) {
            return "";
        }
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
    }

    private boolean containsCjk(String text) {
        if (StrUtil.isBlank(text)) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch >= '\u4e00' && ch <= '\u9fff') {
                return true;
            }
        }
        return false;
    }

    private String titleCaseEnglishTag(String tag) {
        if (StrUtil.isBlank(tag)) {
            return "";
        }
        String[] words = tag.trim().split("\\s+");
        List<String> converted = new ArrayList<String>();
        for (String word : words) {
            if (StrUtil.isBlank(word)) {
                continue;
            }
            String lower = word.toLowerCase(Locale.ROOT);
            if (word.length() <= 3 && word.equals(word.toUpperCase(Locale.ROOT))) {
                converted.add(word);
            } else if (lower.length() == 1) {
                converted.add(lower.toUpperCase(Locale.ROOT));
            } else {
                converted.add(Character.toUpperCase(lower.charAt(0)) + lower.substring(1));
            }
        }
        return String.join(" ", converted);
    }

    private String normalizeForLlmInput(String text) {
        String raw = StrUtil.trimToEmpty(text);
        if (raw.isEmpty()) {
            return raw;
        }
        return Normalizer.normalize(raw, Normalizer.Form.NFKC);
    }

    private String extractCompleteSize(String title, String description) {
        String merged = normalizeForLlmInput(title) + "\n" + normalizeForLlmInput(description);
        Matcher labeled = SIZE_LABELED_PATTERN.matcher(merged);
        if (labeled.find()) {
            return normalizeSizeToken(labeled.group(1));
        }
        Matcher bare = SIZE_BARE_PATTERN.matcher(merged);
        if (bare.find()) {
            return normalizeSizeToken(bare.group(1));
        }
        return "";
    }

    private String normalizeSizeToken(String rawSize) {
        String size = StrUtil.trimToEmpty(rawSize);
        if (size.isEmpty()) {
            return "";
        }
        size = size.replace('×', 'x').replace('X', 'x');
        size = size.replaceAll("\\s*[x]\\s*", "x");
        size = size.replaceAll("\\s+", " ").trim();
        return size;
    }

    private String ensureZhDescriptionContainsSize(String description, String size) {
        String normalizedSize = normalizeSizeToken(size);
        if (StrUtil.isBlank(normalizedSize)) {
            return description;
        }
        String desc = StrUtil.trimToEmpty(description);
        if (containsSize(desc, normalizedSize)) {
            return desc;
        }
        if (StrUtil.isBlank(desc)) {
            return "尺寸：" + normalizedSize;
        }
        return desc + " 尺寸：" + normalizedSize;
    }

    private String ensureEnDescriptionContainsSize(String description, String size) {
        String normalizedSize = normalizeSizeToken(size);
        if (StrUtil.isBlank(normalizedSize)) {
            return description;
        }
        String desc = StrUtil.trimToEmpty(description);
        if (containsSize(desc, normalizedSize)) {
            return desc;
        }
        if (StrUtil.isBlank(desc)) {
            return "Size: " + normalizedSize;
        }
        return desc + " Size: " + normalizedSize;
    }

    private boolean containsSize(String description, String size) {
        String desc = normalizeForLlmInput(description).toLowerCase(Locale.ROOT).replace(" ", "");
        String token = normalizeForLlmInput(size).toLowerCase(Locale.ROOT).replace(" ", "");
        return StrUtil.isNotBlank(desc) && StrUtil.isNotBlank(token) && desc.contains(token);
    }

    private void updateLocalizedProductContent(Long productId, String title, String description) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            return;
        }
        String newTitle = StrUtil.blankToDefault(title, product.getTitle());
        String newDescription = StrUtil.blankToDefault(description, product.getDescription());
        if (StrUtil.equals(product.getTitle(), newTitle) && StrUtil.equals(product.getDescription(), newDescription)) {
            return;
        }
        Product update = new Product();
        update.setId(productId);
        update.setTitle(newTitle);
        update.setDescription(newDescription);
        productMapper.updateById(update);
    }

    private boolean shouldClearDescriptionByShortCaption(boolean allowEmptyDescriptionForShortCaption, String description) {
        if (!allowEmptyDescriptionForShortCaption) {
            return false;
        }
        String d = StrUtil.trimToEmpty(description);
        if (StrUtil.isBlank(d)) {
            return false;
        }
        return d.length() <= 40;
    }

    private String resolveLlmDescriptionSource(String title, String description) {
        if (StrUtil.isNotBlank(description)) {
            return description;
        }
        if (isShortCaption(title)) {
            return "";
        }
        return title;
    }

    private List<String> collectFallbackImageCandidates(WsAlbumProductImport row) {
        List<String> candidates = new ArrayList<String>();
        addCandidate(candidates, row.getMainImageUrl());
        addCandidate(candidates, row.getVideoThumbImg());

        if (StrUtil.isNotBlank(row.getRawJson())) {
            try {
                JsonNode raw = objectMapper.readTree(row.getRawJson());
                JsonNode imgsSrc = raw.path("imgsSrc");
                if (imgsSrc.isArray()) {
                    for (JsonNode node : imgsSrc) {
                        addCandidate(candidates, node.asText());
                    }
                }
                JsonNode imgs = raw.path("imgs");
                if (imgs.isArray()) {
                    for (JsonNode node : imgs) {
                        addCandidate(candidates, stripQuery(node.asText()));
                    }
                }
                addCandidate(candidates, raw.path("videoThumbImg").asText());
                addCandidate(candidates, raw.path("user_icon").asText());
            } catch (Exception ignore) {
                // keep fallback robust even if raw json is malformed
            }
        }

        WsAlbumShop shop = wsAlbumShopMapper.selectOne(new LambdaQueryWrapper<WsAlbumShop>()
                .eq(WsAlbumShop::getShopId, row.getShopId())
                .last("limit 1"));
        if (shop != null) {
            addCandidate(candidates, shop.getIcon());
            addCandidate(candidates, shop.getBanner());
            addCandidate(candidates, shop.getQrcode());
        }

        if (candidates.size() > 9) {
            return new ArrayList<String>(candidates.subList(0, 9));
        }
        return candidates;
    }

    private void addCandidate(List<String> list, String value) {
        if (StrUtil.isBlank(value)) {
            return;
        }
        String normalized = value.trim();
        if (StrUtil.isBlank(normalized)) {
            return;
        }
        if (!list.contains(normalized)) {
            list.add(normalized);
        }
    }

    private List<String> resolveTargetTags(WsAlbumProductImport row, List<String> targetTags, List<String> extraTags) {
        LinkedHashSet<String> merged = new LinkedHashSet<String>();
        if (targetTags != null && !targetTags.isEmpty()) {
            for (String tag : targetTags) {
                if (StrUtil.isNotBlank(tag)) {
                    appendExpandedTags(merged, tag);
                }
            }
        }

        List<WsAlbumProductImportTagRel> sourceTags = wsAlbumProductImportTagRelMapper.selectList(new LambdaQueryWrapper<WsAlbumProductImportTagRel>()
                .eq(WsAlbumProductImportTagRel::getShopId, row.getShopId())
                .eq(WsAlbumProductImportTagRel::getGoodsId, row.getGoodsId())
                .orderByAsc(WsAlbumProductImportTagRel::getId));
        if (sourceTags != null && !sourceTags.isEmpty()) {
            for (WsAlbumProductImportTagRel sourceTag : sourceTags) {
                if (sourceTag != null && StrUtil.isNotBlank(sourceTag.getTagName())) {
                    appendExpandedTags(merged, sourceTag.getTagName());
                }
            }
        }
        if (extraTags != null && !extraTags.isEmpty()) {
            for (String extra : extraTags) {
                if (StrUtil.isNotBlank(extra)) {
                    appendExpandedTags(merged, extra);
                }
            }
        }
        return new ArrayList<String>(merged);
    }

    private void appendExpandedTags(Set<String> collector, String rawTag) {
        if (StrUtil.isBlank(rawTag)) {
            return;
        }
        String normalized = Normalizer.normalize(rawTag, Normalizer.Form.NFKC).trim();
        if (normalized.isEmpty()) {
            return;
        }
        String[] groups = normalized.split("[,，;/|、]+");
        for (String group : groups) {
            String segment = StrUtil.trimToEmpty(group);
            if (segment.isEmpty()) {
                continue;
            }
            String[] tokens = segment.contains(" ") ? segment.split("\\s+") : new String[]{segment};
            for (String token : tokens) {
                String tag = StrUtil.trimToEmpty(token);
                if (tag.isEmpty() || isLikelyDimensionTag(tag)) {
                    continue;
                }
                collector.add(tag);
            }
        }
    }

    private String resolveProductTitle(WsAlbumProductImport row, WsAlbumImportFormalRequest request) {
        if (StrUtil.isNotBlank(row.getTitle())) {
            return row.getTitle().trim();
        }
        if (StrUtil.isNotBlank(request.getDefaultTitleTemplate())) {
            return request.getDefaultTitleTemplate().replace("{goodsId}", row.getGoodsId());
        }
        return "导入商品-" + row.getGoodsId();
    }

    private String resolveProductDescription(WsAlbumProductImport row) {
        if (StrUtil.isNotBlank(row.getSubTitle())) {
            return row.getSubTitle().trim();
        }
        return "";
    }

    private java.math.BigDecimal resolveProductPrice(String itemPrice,
                                                     String priceStrategyType,
                                                     java.math.BigDecimal priceStrategyValue,
                                                     java.math.BigDecimal detectedPriceFromText,
                                                     PriceDetectConfig priceDetectConfig) {
        String strategy = StrUtil.blankToDefault(priceStrategyType, "NONE").toUpperCase(Locale.ROOT);
        java.math.BigDecimal sourcePrice = resolvePriceSourceByPriority(parsePrice(itemPrice), detectedPriceFromText, priceDetectConfig);

        if ("FIXED_OVERRIDE".equals(strategy)) {
            if (priceStrategyValue == null) {
                throw new BusinessException("固定价格覆盖需要填写价格");
            }
            if (priceStrategyValue.compareTo(java.math.BigDecimal.ZERO) < 0) {
                throw new BusinessException("价格不能为负数");
            }
            return priceStrategyValue;
        }

        if (sourcePrice == null) {
            // If source price is empty/non-numeric, keep importable.
            // Percentage/fixed-add strategy becomes ineffective.
            return java.math.BigDecimal.ZERO.setScale(2, java.math.RoundingMode.HALF_UP);
        }

        if ("PERCENT".equals(strategy)) {
            if (priceStrategyValue == null) {
                throw new BusinessException("百分比加价需要填写数值");
            }
            return sourcePrice.multiply(java.math.BigDecimal.ONE.add(priceStrategyValue.divide(new java.math.BigDecimal("100"), 4, java.math.RoundingMode.HALF_UP)))
                    .setScale(2, java.math.RoundingMode.HALF_UP);
        }
        if ("FIXED_ADD".equals(strategy)) {
            if (priceStrategyValue == null) {
                throw new BusinessException("固定金额加价需要填写数值");
            }
            return sourcePrice.add(priceStrategyValue).setScale(2, java.math.RoundingMode.HALF_UP);
        }
        return sourcePrice.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private java.math.BigDecimal parsePrice(String priceText) {
        if (StrUtil.isBlank(priceText)) {
            return null;
        }
        String cleaned = priceText.replace("¥", "")
                .replace("￥", "")
                .replace(",", "")
                .trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        try {
            return new java.math.BigDecimal(cleaned);
        } catch (Exception e) {
            return null;
        }
    }

    private PriceDetectConfig resolvePriceDetectConfig(String shopId, Map<String, PriceDetectConfig> cache) {
        String key = StrUtil.blankToDefault(StrUtil.trim(shopId), "");
        if (cache != null && cache.containsKey(key)) {
            return cache.get(key);
        }
        PriceDetectConfig config = loadPriceDetectConfig(shopId);
        if (cache != null) {
            cache.put(key, config);
        }
        return config;
    }

    private PriceDetectConfig loadPriceDetectConfig(String shopId) {
        if (StrUtil.isBlank(shopId)) {
            return PriceDetectConfig.defaultConfig();
        }
        WsAlbumShop shop = wsAlbumShopMapper.selectOne(new LambdaQueryWrapper<WsAlbumShop>()
                .eq(WsAlbumShop::getShopId, shopId)
                .last("limit 1"));
        if (shop == null || StrUtil.isBlank(shop.getPriceDetectConfigJson())) {
            return PriceDetectConfig.defaultConfig();
        }
        return parsePriceDetectConfig(shop.getPriceDetectConfigJson());
    }

    private String normalizePriceDetectConfigJson(String rawJson) {
        String trimmed = StrUtil.trimToEmpty(rawJson);
        if (trimmed.isEmpty()) {
            return null;
        }
        PriceDetectConfig parsed = parsePriceDetectConfig(trimmed);
        ObjectNode node = objectMapper.createObjectNode();
        node.put("sourcePriority", parsed.sourcePriority);
        node.put("matchMode", parsed.matchMode);
        node.put("priceTransformType", parsed.priceTransformType);
        node.put("transformDigits", parsed.transformDigits);
        node.put("detectInTitle", parsed.detectInTitle);
        node.put("detectInSubTitle", parsed.detectInSubTitle);
        node.put("stripTokens", parsed.stripTokens);
        ArrayNode rules = node.putArray("priceRegexList");
        for (String regex : parsed.priceRegexList) {
            rules.add(regex);
        }
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            throw new BusinessException("价格识别策略保存失败: " + e.getMessage());
        }
    }

    private PriceDetectConfig parsePriceDetectConfig(String rawJson) {
        if (StrUtil.isBlank(rawJson)) {
            return PriceDetectConfig.defaultConfig();
        }
        try {
            JsonNode node = objectMapper.readTree(rawJson);
            PriceDetectConfig config = PriceDetectConfig.defaultConfig();
            config.sourcePriority = normalizeSourcePriority(textValue(node, "sourcePriority"));
            config.matchMode = normalizeMatchMode(textValue(node, "matchMode"));
            config.priceTransformType = normalizeTransformType(textValue(node, "priceTransformType"));
            config.transformDigits = intValue(node.path("transformDigits").asText());
            if (config.transformDigits == null || config.transformDigits <= 0) {
                config.transformDigits = 4;
            }
            if (config.transformDigits > 16) {
                throw new BusinessException("价格识别策略 transformDigits 不能大于16");
            }
            config.detectInTitle = boolValue(node, "detectInTitle", true);
            config.detectInSubTitle = boolValue(node, "detectInSubTitle", true);
            config.stripTokens = boolValue(node, "stripTokens", true);
            config.priceRegexList = parseRegexList(node.path("priceRegexList"));
            config.customDetectPatterns = buildPatternList(config.priceRegexList, false);
            config.customTokenPatterns = buildPatternList(config.priceRegexList, true);
            return config;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception e) {
            throw new BusinessException("价格识别策略JSON格式错误: " + e.getMessage());
        }
    }

    private String normalizeSourcePriority(String raw) {
        String value = StrUtil.blankToDefault(raw, PRICE_PRIORITY_ITEM_FIRST).trim().toUpperCase(Locale.ROOT);
        if (PRICE_PRIORITY_ITEM_FIRST.equals(value)
                || PRICE_PRIORITY_TEXT_FIRST.equals(value)
                || PRICE_PRIORITY_TEXT_ONLY.equals(value)
                || PRICE_PRIORITY_ITEM_ONLY.equals(value)) {
            return value;
        }
        throw new BusinessException("价格识别策略 sourcePriority 不支持: " + raw);
    }

    private String normalizeMatchMode(String raw) {
        String value = StrUtil.blankToDefault(raw, PRICE_MATCH_FIRST_HIT).trim().toUpperCase(Locale.ROOT);
        if (PRICE_MATCH_FIRST_HIT.equals(value) || PRICE_MATCH_MAX.equals(value) || PRICE_MATCH_MIN.equals(value)) {
            return value;
        }
        throw new BusinessException("价格识别策略 matchMode 不支持: " + raw);
    }

    private String normalizeTransformType(String raw) {
        String value = StrUtil.blankToDefault(raw, PRICE_TRANSFORM_NONE).trim().toUpperCase(Locale.ROOT);
        if (PRICE_TRANSFORM_NONE.equals(value) || PRICE_TRANSFORM_REVERSE_LAST_N.equals(value)) {
            return value;
        }
        throw new BusinessException("价格识别策略 priceTransformType 不支持: " + raw);
    }

    private List<String> parseRegexList(JsonNode regexNode) {
        List<String> list = new ArrayList<String>();
        if (regexNode == null || regexNode.isMissingNode() || regexNode.isNull()) {
            return list;
        }
        if (!regexNode.isArray()) {
            throw new BusinessException("价格识别策略 priceRegexList 必须是数组");
        }
        for (JsonNode node : regexNode) {
            if (node == null || node.isNull()) {
                continue;
            }
            String regex = StrUtil.trimToEmpty(node.asText());
            if (!regex.isEmpty()) {
                list.add(regex);
            }
        }
        return list;
    }

    private List<Pattern> buildPatternList(List<String> regexList, boolean tokenPattern) {
        List<Pattern> patterns = new ArrayList<Pattern>();
        if (regexList == null || regexList.isEmpty()) {
            return patterns;
        }
        for (String regex : regexList) {
            try {
                Pattern detectPattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
                if (detectPattern.matcher("").groupCount() < 1) {
                    throw new BusinessException("价格识别正则必须包含至少1个捕获组: " + regex);
                }
                if (tokenPattern) {
                    patterns.add(Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
                } else {
                    patterns.add(detectPattern);
                }
            } catch (BusinessException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new BusinessException("价格识别正则无效: " + regex);
            }
        }
        return patterns;
    }

    private boolean boolValue(JsonNode node, String field, boolean defaultValue) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return defaultValue;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.isMissingNode()) {
            return defaultValue;
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isNumber()) {
            return value.asInt() != 0;
        }
        String text = StrUtil.trimToEmpty(value.asText());
        if (text.isEmpty()) {
            return defaultValue;
        }
        if ("true".equalsIgnoreCase(text) || "1".equals(text)) {
            return true;
        }
        if ("false".equalsIgnoreCase(text) || "0".equals(text)) {
            return false;
        }
        return defaultValue;
    }

    private String buildPriceDetectContent(String title, String description, PriceDetectConfig priceDetectConfig) {
        PriceDetectConfig config = priceDetectConfig == null ? PriceDetectConfig.defaultConfig() : priceDetectConfig;
        StringBuilder builder = new StringBuilder();
        if (config.detectInTitle) {
            builder.append(StrUtil.trimToEmpty(title));
        }
        if (config.detectInSubTitle) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(StrUtil.trimToEmpty(description));
        }
        if (builder.length() == 0) {
            return StrUtil.trimToEmpty(title) + "\n" + StrUtil.trimToEmpty(description);
        }
        return builder.toString();
    }

    private java.math.BigDecimal resolvePriceSourceByPriority(java.math.BigDecimal itemPrice,
                                                              java.math.BigDecimal detectedPriceFromText,
                                                              PriceDetectConfig priceDetectConfig) {
        PriceDetectConfig config = priceDetectConfig == null ? PriceDetectConfig.defaultConfig() : priceDetectConfig;
        if (PRICE_PRIORITY_TEXT_FIRST.equals(config.sourcePriority)) {
            return firstNonNull(detectedPriceFromText, itemPrice);
        }
        if (PRICE_PRIORITY_TEXT_ONLY.equals(config.sourcePriority)) {
            return detectedPriceFromText;
        }
        if (PRICE_PRIORITY_ITEM_ONLY.equals(config.sourcePriority)) {
            return itemPrice;
        }
        return firstNonNull(itemPrice, detectedPriceFromText);
    }

    private NormalizedImportContent normalizeImportContent(String title,
                                                           String description,
                                                           String detectTitle,
                                                           String detectDescription,
                                                           PriceDetectConfig priceDetectConfig) {
        String rawTitle = StrUtil.trimToEmpty(title);
        String rawDescription = StrUtil.trimToEmpty(description);
        String detectContent = buildPriceDetectContent(StrUtil.trimToEmpty(detectTitle),
                StrUtil.trimToEmpty(detectDescription),
                priceDetectConfig);
        java.math.BigDecimal detectedPrice = extractPriceFromText(detectContent, priceDetectConfig);
        List<String> codeTags = extractCodeTags(rawTitle, rawDescription, detectedPrice);
        String cleanedTitle = normalizeTitleText(stripPriceTokens(rawTitle, priceDetectConfig));
        String cleanedDescription = normalizeDescriptionText(stripPriceTokens(rawDescription, priceDetectConfig));
        if (StrUtil.isBlank(cleanedTitle)) {
            cleanedTitle = normalizeTitleText(rawTitle);
        }
        return new NormalizedImportContent(cleanedTitle, cleanedDescription, detectedPrice, codeTags);
    }

    private DetectContentSource resolveDetectContentSource(WsAlbumProductImport row,
                                                           String fallbackTitle,
                                                           String fallbackDescription) {
        String detectTitle = StrUtil.trimToEmpty(fallbackTitle);
        String detectDescription = StrUtil.trimToEmpty(fallbackDescription);
        if (row == null || StrUtil.isBlank(row.getRawJson())) {
            return new DetectContentSource(detectTitle, detectDescription);
        }
        try {
            JsonNode raw = objectMapper.readTree(row.getRawJson());
            String rawTitle = StrUtil.trimToEmpty(textValue(raw, "title"));
            String rawSubTitle = StrUtil.trimToEmpty(textValue(raw, "subTitle"));
            if (shouldUseRawForDetect(detectTitle, rawTitle)) {
                detectTitle = rawTitle;
            }
            if (shouldUseRawForDetect(detectDescription, rawSubTitle)) {
                detectDescription = rawSubTitle;
            }
        } catch (Exception ignore) {
            // Keep import robust if raw JSON cannot be parsed.
        }
        return new DetectContentSource(detectTitle, detectDescription);
    }

    private boolean shouldUseRawForDetect(String currentText, String rawText) {
        String current = StrUtil.trimToEmpty(currentText);
        String raw = StrUtil.trimToEmpty(rawText);
        if (StrUtil.isBlank(raw)) {
            return false;
        }
        if (StrUtil.isBlank(current)) {
            return true;
        }
        if (raw.length() > current.length()) {
            return true;
        }
        return containsEightDigits(raw) && !containsEightDigits(current);
    }

    private boolean containsEightDigits(String text) {
        return StrUtil.isNotBlank(text) && EIGHT_DIGIT_PATTERN.matcher(text).find();
    }

    private java.math.BigDecimal extractPriceFromText(String content, PriceDetectConfig priceDetectConfig) {
        if (StrUtil.isBlank(content)) {
            return null;
        }
        PriceDetectConfig config = priceDetectConfig == null ? PriceDetectConfig.defaultConfig() : priceDetectConfig;
        List<java.math.BigDecimal> matches = new ArrayList<java.math.BigDecimal>();
        List<Pattern> patterns = config.customDetectPatterns;
        if (patterns == null || patterns.isEmpty()) {
            java.math.BigDecimal price = extractPriceByPatternAndTransform(content, PRICE_PATTERN_P, config);
            if (price != null) {
                return price;
            }
            price = extractPriceByPatternAndTransform(content, PRICE_PATTERN_CURRENCY_PREFIX, config);
            if (price != null) {
                return price;
            }
            return extractPriceByPatternAndTransform(content, PRICE_PATTERN_CURRENCY_SUFFIX, config);
        }
        for (Pattern pattern : patterns) {
            if (pattern == null) {
                continue;
            }
            Matcher matcher = pattern.matcher(content);
            while (matcher.find()) {
                String number = matcher.groupCount() >= 1 ? matcher.group(1) : null;
                if (StrUtil.isBlank(number)) {
                    continue;
                }
                java.math.BigDecimal transformed = transformDetectedPrice(number, config);
                if (transformed != null) {
                    matches.add(transformed);
                    if (PRICE_MATCH_FIRST_HIT.equals(config.matchMode)) {
                        return matches.get(0);
                    }
                }
            }
        }
        if (matches.isEmpty()) {
            return null;
        }
        if (PRICE_MATCH_MIN.equals(config.matchMode)) {
            return matches.stream().min(java.math.BigDecimal::compareTo).orElse(matches.get(0));
        }
        if (PRICE_MATCH_MAX.equals(config.matchMode)) {
            return matches.stream().max(java.math.BigDecimal::compareTo).orElse(matches.get(0));
        }
        return matches.get(0);
    }

    private java.math.BigDecimal transformDetectedPrice(String number, PriceDetectConfig config) {
        if (StrUtil.isBlank(number)) {
            return null;
        }
        String cleaned = number.trim();
        try {
            if (PRICE_TRANSFORM_REVERSE_LAST_N.equals(config.priceTransformType)) {
                String digits = cleaned.replaceAll("\\D", "");
                if (StrUtil.isBlank(digits)) {
                    return null;
                }
                int length = config.transformDigits == null || config.transformDigits <= 0 ? 4 : config.transformDigits;
                if (digits.length() < length) {
                    return null;
                }
                String tail = digits.substring(digits.length() - length);
                String reversed = new StringBuilder(tail).reverse().toString();
                return new java.math.BigDecimal(reversed);
            }
            return new java.math.BigDecimal(cleaned);
        } catch (Exception ignore) {
            return null;
        }
    }

    private java.math.BigDecimal extractPriceByPattern(String text, Pattern pattern) {
        if (StrUtil.isBlank(text) || pattern == null) {
            return null;
        }
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        String number = matcher.groupCount() >= 1 ? matcher.group(1) : null;
        if (StrUtil.isBlank(number)) {
            return null;
        }
        try {
            return new java.math.BigDecimal(number.trim());
        } catch (Exception ignore) {
            return null;
        }
    }

    private java.math.BigDecimal extractPriceByPatternAndTransform(String text, Pattern pattern, PriceDetectConfig config) {
        if (StrUtil.isBlank(text) || pattern == null) {
            return null;
        }
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        String number = matcher.groupCount() >= 1 ? matcher.group(1) : null;
        return transformDetectedPrice(number, config == null ? PriceDetectConfig.defaultConfig() : config);
    }

    private String stripPriceTokens(String text) {
        return stripPriceTokens(text, PriceDetectConfig.defaultConfig());
    }

    private String stripPriceTokens(String text, PriceDetectConfig priceDetectConfig) {
        if (StrUtil.isBlank(text)) {
            return "";
        }
        if (priceDetectConfig != null && !priceDetectConfig.stripTokens) {
            return text.trim();
        }
        String cleaned = text;
        List<Pattern> tokenPatterns = priceDetectConfig == null ? null : priceDetectConfig.customTokenPatterns;
        if (tokenPatterns != null && !tokenPatterns.isEmpty()) {
            for (Pattern tokenPattern : tokenPatterns) {
                if (tokenPattern != null) {
                    cleaned = tokenPattern.matcher(cleaned).replaceAll(" ");
                }
            }
        } else {
            cleaned = PRICE_TOKEN_PATTERN_P.matcher(cleaned).replaceAll(" ");
            cleaned = PRICE_TOKEN_PATTERN_CURRENCY_PREFIX.matcher(cleaned).replaceAll(" ");
            cleaned = PRICE_TOKEN_PATTERN_CURRENCY_SUFFIX.matcher(cleaned).replaceAll(" ");
            cleaned = PRICE_TOKEN_PATTERN_PRICE_WORD.matcher(cleaned).replaceAll(" ");
        }
        cleaned = cleaned.replaceAll("[ \\t]{2,}", " ");
        cleaned = cleaned.replaceAll("\\s*([,，;；:：])\\s*", "$1 ");
        cleaned = cleaned.replaceAll("^[,，;；:：\\-_/|\\s]+", "");
        cleaned = cleaned.replaceAll("[,，;；:：\\-_/|\\s]+$", "");
        cleaned = cleaned.replaceAll("\\n{3,}", "\n\n");
        return cleaned.trim();
    }

    private List<String> extractCodeTags(String title, String description, java.math.BigDecimal detectedPrice) {
        LinkedHashSet<String> codes = new LinkedHashSet<String>();
        String source = StrUtil.trimToEmpty(title) + "\n" + StrUtil.trimToEmpty(description);

        Matcher labeled = CODE_LABELED_PATTERN.matcher(source);
        while (labeled.find()) {
            addCodeTag(codes, labeled.group(1), detectedPrice);
        }

        Matcher alphaNum = CODE_ALNUM_PATTERN.matcher(source);
        while (alphaNum.find()) {
            addCodeTag(codes, alphaNum.group(), detectedPrice);
        }

        Matcher digitPrefix = CODE_DIGIT_PREFIX_PATTERN.matcher(source);
        while (digitPrefix.find()) {
            addCodeTag(codes, digitPrefix.group(), detectedPrice);
        }

        Matcher numeric = CODE_NUMERIC_PATTERN.matcher(source);
        while (numeric.find()) {
            addCodeTag(codes, numeric.group(), detectedPrice);
        }
        return new ArrayList<String>(codes);
    }

    private void addCodeTag(Set<String> collector, String rawToken, java.math.BigDecimal detectedPrice) {
        if (StrUtil.isBlank(rawToken)) {
            return;
        }
        String code = rawToken.trim().replaceAll("^[^A-Za-z0-9]+", "").replaceAll("[^A-Za-z0-9]+$", "");
        if (StrUtil.isBlank(code)) {
            return;
        }
        if (code.length() < 3 || code.length() > 24) {
            return;
        }
        if (isLikelyPriceToken(code, detectedPrice)) {
            return;
        }
        if (isLikelyDimensionTag(code)) {
            return;
        }
        if (code.matches("20\\d{2}") || code.matches("19\\d{2}")) {
            return;
        }
        if (code.matches("[A-Za-z].*")) {
            code = code.toUpperCase(Locale.ROOT);
        }
        collector.add(code);
    }

    private boolean isLikelyPriceToken(String token, java.math.BigDecimal detectedPrice) {
        if (StrUtil.isBlank(token)) {
            return false;
        }
        String normalized = token.trim();
        if (normalized.matches("(?i)^P\\d{2,6}(?:\\.\\d{1,2})?$")) {
            return true;
        }
        if (detectedPrice == null) {
            return false;
        }
        String pricePlain = detectedPrice.stripTrailingZeros().toPlainString();
        String priceInt = detectedPrice.setScale(0, java.math.RoundingMode.DOWN).toPlainString();
        return normalized.equalsIgnoreCase(pricePlain) || normalized.equalsIgnoreCase(priceInt);
    }

    private boolean isLikelyDimensionTag(String token) {
        if (StrUtil.isBlank(token)) {
            return false;
        }
        String normalized = token.trim().replace('×', 'x');
        return TAG_DIMENSION_PATTERN.matcher(normalized).matches();
    }

    private String normalizeTitleText(String text) {
        if (StrUtil.isBlank(text)) {
            return "";
        }
        String normalized = text.replace("\r", " ").replace("\n", " ");
        normalized = normalized.replaceAll("\\s{2,}", " ").trim();
        return normalized;
    }

    private String normalizeDescriptionText(String text) {
        if (StrUtil.isBlank(text)) {
            return "";
        }
        String normalized = text.replace("\r", "");
        normalized = normalized.replaceAll("[ \\t]{2,}", " ");
        normalized = normalized.replaceAll(" *\\n *", "\n");
        normalized = normalized.replaceAll("\\n{3,}", "\n\n");
        return normalized.trim();
    }

    private boolean isShortCaption(String text) {
        if (StrUtil.isBlank(text)) {
            return false;
        }
        String normalized = text.replaceAll("\\s+", "").trim();
        if (normalized.length() <= 10) {
            return true;
        }
        String[] words = text.trim().split("\\s+");
        return words.length > 0 && words.length <= 6;
    }

    private String buildReadableSku() {
        String datePart = DateUtil.format(new Date(), "yyMMdd");
        String prefix = "PG-" + datePart + "-";
        int nextSeq = loadNextSkuSeq(prefix);
        for (int i = 0; i < 1000; i++) {
            String candidate = prefix + String.format("%04d", nextSeq + i);
            if (!existsSku(candidate)) {
                return candidate;
            }
        }
        return prefix + IdUtil.fastSimpleUUID().substring(0, 4).toUpperCase(Locale.ROOT);
    }

    private int loadNextSkuSeq(String prefix) {
        Product latest = productMapper.selectOne(new LambdaQueryWrapper<Product>()
                .likeRight(Product::getSku, prefix)
                .orderByDesc(Product::getSku)
                .last("limit 1"));
        if (latest == null || StrUtil.isBlank(latest.getSku())) {
            return 1;
        }
        String sku = latest.getSku();
        if (!sku.startsWith(prefix)) {
            return 1;
        }
        String suffix = sku.substring(prefix.length());
        if (!suffix.matches("\\d{4}")) {
            return 1;
        }
        try {
            return Integer.parseInt(suffix) + 1;
        } catch (Exception ignore) {
            return 1;
        }
    }

    private boolean existsSku(String sku) {
        Long count = productMapper.selectCount(new LambdaQueryWrapper<Product>().eq(Product::getSku, sku));
        return count != null && count > 0;
    }

    private UploadResult resolveSourceToOss(String sourceUrl, String mediaType) {
        return resolveSourceToOss(sourceUrl, mediaType, true);
    }

    private UploadResult resolveSourceToOss(String sourceUrl, String mediaType, boolean allowEndpointRetry) {
        if (StrUtil.isBlank(sourceUrl)) {
            throw new BusinessException("资源URL为空");
        }
        WsAlbumMediaMap map = wsAlbumMediaMapMapper.selectOne(new LambdaQueryWrapper<WsAlbumMediaMap>()
                .eq(WsAlbumMediaMap::getSourceUrl, sourceUrl));
        if (map != null && StrUtil.isNotBlank(map.getOssUrl())) {
            map.setRefCount((map.getRefCount() == null ? 0 : map.getRefCount()) + 1);
            wsAlbumMediaMapMapper.updateById(map);
            return new UploadResult(map.getOssKey(), map.getOssUrl());
        }

        ensureOssReady();
        OSS ossClient = null;
        String endpoint = getActiveOssEndpoint();
        try {
            ossClient = new OSSClientBuilder().build(endpoint, ossProperties.getAccessKeyId(), ossProperties.getAccessKeySecret());

            HttpResponse response = HttpRequest.get(sourceUrl)
                    .timeout(30000)
                    .execute();
            if (response.getStatus() >= 400) {
                throw new BusinessException("下载资源失败，HTTP=" + response.getStatus());
            }
            byte[] bytes = response.bodyBytes();
            if (bytes == null || bytes.length == 0) {
                throw new BusinessException("下载资源失败，内容为空");
            }

            String ext = parseExtensionFromUrl(sourceUrl);
            String datePath = DateUtil.format(new Date(), "yyyy/MM/dd");
            String ossKey = "ws-import/" + mediaType + "/" + datePath + "/" + IdUtil.fastSimpleUUID() + "." + ext;

            ossClient.putObject(ossProperties.getBucketName(), ossKey, new ByteArrayInputStream(bytes));
            // Respect bucket-level ACL policy; do not force per-object ACL here.

            String ossUrl = buildOssUrl(ossProperties.getBucketName(), endpoint, ossKey, ossProperties.getBucketDomain());

            if (map == null) {
                map = new WsAlbumMediaMap();
                map.setSourceUrl(sourceUrl);
            }
            map.setOssKey(ossKey);
            map.setOssUrl(ossUrl);
            map.setMediaType(mediaType);
            map.setRefCount((map.getRefCount() == null ? 0 : map.getRefCount()) + 1);

            if (map.getId() == null) {
                wsAlbumMediaMapMapper.insert(map);
            } else {
                wsAlbumMediaMapMapper.updateById(map);
            }

            return new UploadResult(ossKey, ossUrl);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            String suggestedEndpoint = extractSuggestedEndpoint(e);
            if (allowEndpointRetry && StrUtil.isNotBlank(suggestedEndpoint)) {
                String normalized = normalizeEndpoint(suggestedEndpoint);
                if (!sameEndpointHost(endpoint, normalized)) {
                    resolvedOssEndpoint = normalized;
                    ossBucketChecked = false;
                    return resolveSourceToOss(sourceUrl, mediaType, false);
                }
            }
            throw new BusinessException("上传OSS失败: " + e.getMessage());
        } finally {
            if (ossClient != null) {
                ossClient.shutdown();
            }
        }
    }

    private synchronized void ensureOssReady() {
        ossConfigService.refreshRuntimeOssProperties();
        String currentSignature = buildOssConfigSignature();
        if (!StrUtil.equals(currentSignature, ossConfigSignature)) {
            ossConfigSignature = currentSignature;
            ossBucketChecked = false;
            resolvedOssEndpoint = null;
        }

        if (ossBucketChecked) {
            return;
        }
        if (!Boolean.TRUE.equals(ossProperties.getEnabled())) {
            throw new BusinessException("导入正式库需要开启 OSS_ENABLED=true");
        }
        if (StrUtil.hasBlank(ossProperties.getEndpoint(), ossProperties.getBucketName(), ossProperties.getAccessKeyId(), ossProperties.getAccessKeySecret())) {
            throw new BusinessException("OSS配置不完整，请检查 endpoint/bucket/AK/SK");
        }
        String endpoint = getActiveOssEndpoint();
        OSS client = null;
        try {
            client = new OSSClientBuilder().build(endpoint, ossProperties.getAccessKeyId(), ossProperties.getAccessKeySecret());
            if (!client.doesBucketExist(ossProperties.getBucketName())) {
                CreateBucketRequest req = new CreateBucketRequest(ossProperties.getBucketName());
                req.setCannedACL(CannedAccessControlList.PublicRead);
                client.createBucket(req);
            }
            ossBucketChecked = true;
        } catch (Exception e) {
            String suggestedEndpoint = extractSuggestedEndpoint(e);
            if (StrUtil.isNotBlank(suggestedEndpoint)) {
                String normalized = normalizeEndpoint(suggestedEndpoint);
                if (!sameEndpointHost(endpoint, normalized)) {
                    resolvedOssEndpoint = normalized;
                    ossConfigSignature = buildOssConfigSignature();
                    OSS retryClient = null;
                    try {
                        retryClient = new OSSClientBuilder().build(normalized, ossProperties.getAccessKeyId(), ossProperties.getAccessKeySecret());
                        if (!retryClient.doesBucketExist(ossProperties.getBucketName())) {
                            CreateBucketRequest req = new CreateBucketRequest(ossProperties.getBucketName());
                            req.setCannedACL(CannedAccessControlList.PublicRead);
                            retryClient.createBucket(req);
                        }
                        ossBucketChecked = true;
                        return;
                    } catch (Exception retryEx) {
                        throw new BusinessException("OSS初始化失败: " + retryEx.getMessage());
                    } finally {
                        if (retryClient != null) {
                            retryClient.shutdown();
                        }
                    }
                }
            }
            throw new BusinessException("OSS初始化失败: " + e.getMessage());
        } finally {
            if (client != null) {
                client.shutdown();
            }
        }
    }

    private String getActiveOssEndpoint() {
        return normalizeEndpoint(StrUtil.blankToDefault(resolvedOssEndpoint, ossProperties.getEndpoint()));
    }

    private String buildOssConfigSignature() {
        return StrUtil.join("|",
                normalizeEndpoint(ossProperties.getEndpoint()),
                StrUtil.trimToEmpty(ossProperties.getBucketName()),
                StrUtil.trimToEmpty(ossProperties.getAccessKeyId()),
                StrUtil.trimToEmpty(ossProperties.getAccessKeySecret()),
                normalizeBucketDomainForSignature(ossProperties.getBucketDomain()),
                Boolean.TRUE.equals(ossProperties.getEnabled()) ? "1" : "0",
                normalizeEndpoint(resolvedOssEndpoint));
    }

    private String normalizeBucketDomainForSignature(String bucketDomain) {
        if (StrUtil.isBlank(bucketDomain)) {
            return "";
        }
        String value = bucketDomain.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String normalizeEndpoint(String endpoint) {
        if (StrUtil.isBlank(endpoint)) {
            return endpoint;
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

    private boolean sameEndpointHost(String endpointA, String endpointB) {
        if (StrUtil.isBlank(endpointA) || StrUtil.isBlank(endpointB)) {
            return false;
        }
        String hostA = endpointA.replace("https://", "").replace("http://", "");
        String hostB = endpointB.replace("https://", "").replace("http://", "");
        return hostA.equalsIgnoreCase(hostB);
    }

    private String extractSuggestedEndpoint(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        String message = throwable.getMessage();
        if (StrUtil.isBlank(message)) {
            return null;
        }
        Matcher xmlMatcher = Pattern.compile("<Endpoint>([^<]+)</Endpoint>").matcher(message);
        if (xmlMatcher.find()) {
            return xmlMatcher.group(1);
        }
        Matcher plainMatcher = Pattern.compile("Endpoint\\s*[:=]\\s*([a-zA-Z0-9.-]+)").matcher(message);
        if (plainMatcher.find()) {
            return plainMatcher.group(1);
        }
        return null;
    }

    private String parseExtensionFromUrl(String sourceUrl) {
        try {
            URL url = new URL(sourceUrl);
            String path = url.getPath();
            if (StrUtil.isBlank(path) || !path.contains(".")) {
                return "jpg";
            }
            String ext = path.substring(path.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
            if (ext.length() > 8) {
                return "jpg";
            }
            return ext;
        } catch (Exception ignore) {
            return "jpg";
        }
    }

    private String buildOssUrl(String bucketName, String endpoint, String objectKey, String bucketDomain) {
        if (StrUtil.isNotBlank(bucketDomain)) {
            if (bucketDomain.startsWith("http://") || bucketDomain.startsWith("https://")) {
                return bucketDomain + "/" + objectKey;
            }
            return "https://" + bucketDomain + "/" + objectKey;
        }
        String endpointHost = endpoint.replace("https://", "").replace("http://", "");
        return "https://" + bucketName + "." + endpointHost + "/" + objectKey;
    }

    private void submitCrawlTask(final Long logId,
                                 final String batchNo,
                                 final CrawlRuntime runtime,
                                 final String token,
                                 final Long configId,
                                 final String lockShopKey) {
        try {
            Future<?> future = wsAlbumCrawlExecutor.submit(new Runnable() {
                @Override
                public void run() {
                    executeCrawlAsync(logId, batchNo, runtime, token, configId, lockShopKey);
                }
            });
            crawlFutureMap.put(batchNo, future);
        } catch (Exception ex) {
            releaseShopLock(lockShopKey, batchNo);
            WsAlbumCrawlLog failedLog = wsAlbumCrawlLogMapper.selectById(logId);
            if (failedLog != null) {
                failedLog.setStatus(CRAWL_STATUS_FAILED);
                failedLog.setErrorMessage(StrUtil.sub("任务调度失败: " + ex.getMessage(), 0, 1000));
                failedLog.setEndTime(LocalDateTime.now());
                wsAlbumCrawlLogMapper.updateById(failedLog);
            }
            throw ex;
        }
    }

    private void releaseShopLock(String lockShopKey, String batchNo) {
        if (StrUtil.isBlank(lockShopKey)) {
            if (StrUtil.isNotBlank(batchNo)) {
                crawlBatchShopMap.remove(batchNo);
                crawlStopFlagMap.remove(batchNo);
            }
            return;
        }
        if (StrUtil.isBlank(batchNo)) {
            shopRunningBatchMap.remove(lockShopKey);
            return;
        }
        shopRunningBatchMap.remove(lockShopKey, batchNo);
        crawlBatchShopMap.remove(batchNo);
        crawlStopFlagMap.remove(batchNo);
        crawlFutureMap.remove(batchNo);
    }

    private void executeCrawlAsync(Long logId, String batchNo, CrawlRuntime runtime, String token, Long configId, String lockShopKey) {
        WsAlbumCrawlLog log = wsAlbumCrawlLogMapper.selectById(logId);
        if (log == null) {
            releaseShopLock(lockShopKey, batchNo);
            return;
        }

        Long pageTimestamp = runtime.pageTimestampStart;
        int pageCount = 0;
        int fetched = 0;
        int inserted = 0;
        int updated = 0;
        int duplicated = 0;
        int failed = 0;
        boolean stoppedByUser = false;
        boolean stoppedByDuplicate = false;

        String errorMessage = null;
        try {
            if (runtime.itemIds != null && !runtime.itemIds.isEmpty()) {
                int pageNo = 0;
                List<JsonNode> fetchedItems = new ArrayList<JsonNode>();
                for (String itemId : runtime.itemIds) {
                    pageNo++;
                    if (isCrawlStopRequested(batchNo)) {
                        stoppedByUser = true;
                        break;
                    }
                    String requestUrl = buildCommodityViewUrl(runtime, itemId);
                    String requestParamsJson = toJson(buildItemRequestTraceParams(runtime, itemId));
                    FetchItemResult fetchItemResult;
                    try {
                        fetchItemResult = fetchCommodityItemWithMeta(token, runtime, itemId);
                    } catch (Exception itemError) {
                        saveCrawlRequestLog(batchNo, pageNo, requestUrl, requestParamsJson, null,
                                null, null, null, null, 2, itemError.getMessage());
                        if (!runtime.mergeItemIds) {
                            failed++;
                            continue;
                        }
                        throw itemError;
                    }

                    saveCrawlRequestLog(batchNo, pageNo, requestUrl, requestParamsJson, null,
                            null, null, 1, fetchItemResult.httpStatus, 1, null);
                    fetched++;
                    pageCount = pageNo;

                    if (runtime.mergeItemIds) {
                        fetchedItems.add(fetchItemResult.item);
                    } else {
                        ProcessOutcome outcome = processItem(runtime, batchNo, fetchItemResult.item);
                        if (outcome == ProcessOutcome.INSERTED) {
                            inserted++;
                        } else if (outcome == ProcessOutcome.UPDATED) {
                            updated++;
                        } else if (outcome == ProcessOutcome.DUPLICATED) {
                            duplicated++;
                            if (runtime.stopOnDuplicate) {
                                stoppedByDuplicate = true;
                            }
                        } else {
                            failed++;
                        }
                    }

                    log.setPageCount(pageCount);
                    log.setFetchedCount(fetched);
                    log.setInsertedCount(inserted);
                    log.setUpdatedCount(updated);
                    log.setDuplicateCount(duplicated);
                    log.setFailedCount(failed);
                    wsAlbumCrawlLogMapper.updateById(log);
                    if (stoppedByDuplicate) {
                        break;
                    }
                }

                if (!stoppedByUser && runtime.mergeItemIds && !fetchedItems.isEmpty()) {
                    JsonNode mergedItem = buildMergedItemForSpecifiedIds(runtime, fetchedItems);
                    ProcessOutcome mergedOutcome = processItem(runtime, batchNo, mergedItem);
                    if (mergedOutcome == ProcessOutcome.INSERTED) {
                        inserted++;
                    } else if (mergedOutcome == ProcessOutcome.UPDATED) {
                        updated++;
                    } else if (mergedOutcome == ProcessOutcome.DUPLICATED) {
                        duplicated++;
                    } else {
                        failed++;
                    }
                }

                log.setPageCount(pageCount);
                log.setFetchedCount(fetched);
                log.setInsertedCount(inserted);
                log.setUpdatedCount(updated);
                log.setDuplicateCount(duplicated);
                log.setFailedCount(failed);
                log.setPageTimestampEnd(pageTimestamp);
                wsAlbumCrawlLogMapper.updateById(log);
            } else {
                CrawlExecutionStats stats = executePagedCrawl(batchNo, runtime, token, log);
                pageTimestamp = stats.lastPageTimestamp;
                pageCount = stats.pageCount;
                fetched = stats.fetched;
                inserted = stats.inserted;
                updated = stats.updated;
                duplicated = stats.duplicated;
                failed = stats.failed;
                stoppedByUser = stats.stoppedByUser;
                stoppedByDuplicate = stats.stoppedByDuplicate;
            }

            if (stoppedByUser) {
                log.setStatus(CRAWL_STATUS_STOPPED);
                log.setErrorMessage("任务已手动停止");
            } else {
                log.setStatus(CRAWL_STATUS_SUCCESS);
                if (stoppedByDuplicate && runtime.stopOnDuplicate) {
                    log.setErrorMessage("同步抓取命中重复数据，已提前结束");
                }
            }
            log.setEndTime(LocalDateTime.now());
            wsAlbumCrawlLogMapper.updateById(log);

            if (!stoppedByUser) {
                WsAlbumConfig config = wsAlbumConfigMapper.selectById(configId);
                if (config != null) {
                    config.setLastSuccessTime(LocalDateTime.now());
                    wsAlbumConfigMapper.updateById(config);
                }

                WsAlbumShop shop = wsAlbumShopMapper.selectOne(new LambdaQueryWrapper<WsAlbumShop>()
                        .eq(WsAlbumShop::getShopId, runtime.shopId));
                if (shop != null) {
                    shop.setLastCrawlTime(LocalDateTime.now());
                    wsAlbumShopMapper.updateById(shop);
                }
            }
        } catch (Exception ex) {
            errorMessage = ex.getMessage();
            log.setEndTime(LocalDateTime.now());
            log.setPageCount(pageCount);
            log.setFetchedCount(fetched);
            log.setInsertedCount(inserted);
            log.setUpdatedCount(updated);
            log.setDuplicateCount(duplicated);
            log.setPageTimestampEnd(pageTimestamp);
            if (isCrawlStopRequested(batchNo) || log.getStatus() != null && log.getStatus() == CRAWL_STATUS_STOPPED) {
                log.setStatus(CRAWL_STATUS_STOPPED);
                log.setErrorMessage("任务已手动停止");
                log.setFailedCount(failed);
            } else {
                log.setStatus(CRAWL_STATUS_FAILED);
                log.setErrorMessage(StrUtil.sub(errorMessage, 0, 1000));
                log.setFailedCount(failed + 1);
            }
            wsAlbumCrawlLogMapper.updateById(log);
        } finally {
            releaseShopLock(lockShopKey, batchNo);
        }
    }

    private CrawlExecutionStats executePagedCrawl(String batchNo, CrawlRuntime runtime, String token, WsAlbumCrawlLog log) {
        CrawlExecutionStats stats = new CrawlExecutionStats();
        List<CrawlRuntime> scopes = expandTagScopedRuntimes(runtime);
        boolean shopInfoSynced = false;

        for (CrawlRuntime scope : scopes) {
            Long pageTimestamp = scope.pageTimestampStart;
            Set<String> pageSignatures = new HashSet<String>();

            for (int i = 0; i < scope.maxPages; i++) {
                if (isCrawlStopRequested(batchNo)) {
                    stats.stoppedByUser = true;
                    return stats;
                }

                Long requestCursor = pageTimestamp;
                int pageNo = stats.pageCount + 1;
                String requestUrl = buildRequestUrl(scope, requestCursor);
                String requestParamsJson = toJson(buildRequestTraceParams(scope, requestCursor));
                FetchPageResult fetchPageResult;
                JsonNode result;
                try {
                    fetchPageResult = fetchAlbumPageWithMeta(token, scope, requestUrl);
                    result = fetchPageResult.result;
                } catch (Exception pageError) {
                    saveCrawlRequestLog(batchNo, pageNo, requestUrl, requestParamsJson, requestCursor,
                            null, null, null, null, 2, pageError.getMessage());
                    throw pageError;
                }

                if (!shopInfoSynced) {
                    WsAlbumShop shop = upsertShopFromResult(result, runtime.albumId, runtime.remark);
                    runtime.shopId = shop.getShopId();
                    runtime.shopName = shop.getShopName();
                    log.setShopId(runtime.shopId);
                    log.setShopName(runtime.shopName);
                    wsAlbumCrawlLogMapper.updateById(log);
                    shopInfoSynced = true;
                }

                JsonNode items = result.path("items");
                JsonNode pagination = result.path("pagination");

                int currentFetched = items.isArray() ? items.size() : 0;
                String signature = buildPageSignature(items);
                if (StrUtil.isNotBlank(signature) && pageSignatures.contains(signature)) {
                    Long loopStopTs = longValue(pagination, "pageTimestamp");
                    Boolean loopStopHasMore = pagination.path("isLoadMore").isMissingNode() ? null : pagination.path("isLoadMore").asBoolean(false);
                    saveCrawlRequestLog(batchNo, pageNo, requestUrl, requestParamsJson, requestCursor,
                            loopStopTs, loopStopHasMore == null ? null : boolAsInt(loopStopHasMore), currentFetched,
                            fetchPageResult.httpStatus, 1, "重复页签名，提前停止");
                    break;
                }
                if (StrUtil.isNotBlank(signature)) {
                    pageSignatures.add(signature);
                }

                Long nextPageTimestamp = longValue(pagination, "pageTimestamp");
                boolean hasMore = pagination.path("isLoadMore").asBoolean(false);
                saveCrawlRequestLog(batchNo, pageNo, requestUrl, requestParamsJson, requestCursor,
                        nextPageTimestamp, boolAsInt(hasMore), currentFetched, fetchPageResult.httpStatus, 1, null);

                stats.fetched += currentFetched;
                stats.pageCount++;

                if (items.isArray()) {
                    for (JsonNode item : items) {
                        if (isCrawlStopRequested(batchNo)) {
                            stats.stoppedByUser = true;
                            break;
                        }
                        ProcessOutcome outcome = processItem(scope, batchNo, item);
                        if (outcome == ProcessOutcome.INSERTED) {
                            stats.inserted++;
                        } else if (outcome == ProcessOutcome.UPDATED) {
                            stats.updated++;
                        } else if (outcome == ProcessOutcome.DUPLICATED) {
                            stats.duplicated++;
                            if (scope.stopOnDuplicate) {
                                stats.stoppedByDuplicate = true;
                                break;
                            }
                        } else {
                            stats.failed++;
                        }
                    }
                }

                stats.lastPageTimestamp = nextPageTimestamp == null ? pageTimestamp : nextPageTimestamp;
                log.setPageCount(stats.pageCount);
                log.setFetchedCount(stats.fetched);
                log.setInsertedCount(stats.inserted);
                log.setUpdatedCount(stats.updated);
                log.setDuplicateCount(stats.duplicated);
                log.setFailedCount(stats.failed);
                log.setPageTimestampEnd(stats.lastPageTimestamp);
                wsAlbumCrawlLogMapper.updateById(log);

                if (!hasMore || stats.stoppedByUser || stats.stoppedByDuplicate || currentFetched <= 0) {
                    break;
                }
                if (nextPageTimestamp == null || (requestCursor != null && requestCursor.equals(nextPageTimestamp))) {
                    break;
                }
                pageTimestamp = nextPageTimestamp;
            }

            if (stats.stoppedByUser || stats.stoppedByDuplicate) {
                break;
            }
        }
        return stats;
    }

    private List<CrawlRuntime> expandTagScopedRuntimes(CrawlRuntime runtime) {
        List<Long> normalizedTags = normalizeTagIds(runtime == null ? null : runtime.tagList);
        if (runtime == null || normalizedTags.size() <= 1) {
            if (runtime != null) {
                runtime.tagList = normalizedTags;
                if ("INCREMENTAL".equals(runtime.crawlMode) && runtime.startTimestamp == null && runtime.pageTimestampStart == null) {
                    runtime.pageTimestampStart = loadLatestIncrementalTimestamp(runtime);
                }
            }
            return Collections.singletonList(runtime);
        }

        List<CrawlRuntime> scopes = new ArrayList<CrawlRuntime>();
        for (Long tagId : normalizedTags) {
            CrawlRuntime scope = runtime.copy();
            scope.tagList = new ArrayList<Long>();
            scope.tagList.add(tagId);
            if ("INCREMENTAL".equals(scope.crawlMode) && scope.startTimestamp == null) {
                scope.pageTimestampStart = loadLatestIncrementalTimestamp(scope);
            } else {
                scope.pageTimestampStart = scope.startTimestamp;
            }
            scopes.add(scope);
        }
        return scopes;
    }

    private ProcessOutcome processItem(CrawlRuntime runtime, String batchNo, JsonNode item) {
        String goodsId = textValue(item, "goods_id");
        if (StrUtil.isBlank(goodsId)) {
            return ProcessOutcome.FAILED;
        }

        String shopId = firstNonBlank(textValue(item, "shop_id"), runtime.shopId);
        if (StrUtil.isBlank(shopId)) {
            throw new BusinessException("返回数据缺少 shop_id");
        }

        Long incomingUpdateTime = firstNonNull(longValue(item, "update_time"), longValue(item, "time_stamp"), 0L);

        WsAlbumProductImport existing = wsAlbumProductImportMapper.selectOne(new LambdaQueryWrapper<WsAlbumProductImport>()
                .eq(WsAlbumProductImport::getShopId, shopId)
                .eq(WsAlbumProductImport::getGoodsId, goodsId));

        if (existing == null) {
            WsAlbumProductImport entity = buildImportEntity(runtime, batchNo, item, shopId, goodsId, incomingUpdateTime);
            wsAlbumProductImportMapper.insert(entity);
            replaceItemMedia(shopId, goodsId, item);
            replaceItemTags(shopId, goodsId, item);
            return ProcessOutcome.INSERTED;
        }

        long oldTs = firstNonNull(existing.getSourceUpdateTime(), existing.getTimeStamp(), 0L);
        if (runtime.enableTimestampUpdate && incomingUpdateTime != null && incomingUpdateTime > oldTs) {
            WsAlbumProductImport latest = buildImportEntity(runtime, batchNo, item, shopId, goodsId, incomingUpdateTime);
            latest.setId(existing.getId());
            latest.setFirstCrawlTime(existing.getFirstCrawlTime());
            latest.setImportStatus(existing.getImportStatus());
            latest.setFormalProductId(existing.getFormalProductId());
            latest.setDeleted(0);
            wsAlbumProductImportMapper.updateById(latest);
            replaceItemMedia(shopId, goodsId, item);
            replaceItemTags(shopId, goodsId, item);
            return ProcessOutcome.UPDATED;
        }

        return ProcessOutcome.DUPLICATED;
    }

    @Transactional(rollbackFor = Exception.class)
    protected void replaceItemMedia(String shopId, String goodsId, JsonNode item) {
        wsAlbumProductImportMediaMapper.delete(new LambdaQueryWrapper<WsAlbumProductImportMedia>()
                .eq(WsAlbumProductImportMedia::getShopId, shopId)
                .eq(WsAlbumProductImportMedia::getGoodsId, goodsId));

        String videoUrl = firstNonBlank(textValue(item, "videoUrl"), textValue(item, "videoURL"));
        if (StrUtil.isNotBlank(videoUrl)) {
            WsAlbumProductImportMedia media = new WsAlbumProductImportMedia();
            media.setShopId(shopId);
            media.setGoodsId(goodsId);
            media.setMediaType("video");
            media.setSourceUrl(videoUrl);
            media.setSortNo(1);
            media.setStatus(1);
            wsAlbumProductImportMediaMapper.insert(media);
            String videoThumb = textValue(item, "videoThumbImg");
            if (StrUtil.isNotBlank(videoThumb)) {
                WsAlbumProductImportMedia thumbMedia = new WsAlbumProductImportMedia();
                thumbMedia.setShopId(shopId);
                thumbMedia.setGoodsId(goodsId);
                thumbMedia.setMediaType("video_thumb");
                thumbMedia.setSourceUrl(videoThumb);
                thumbMedia.setSortNo(1);
                thumbMedia.setStatus(1);
                wsAlbumProductImportMediaMapper.insert(thumbMedia);
            }
        }

        List<String> imageList = extractImageUrls(item);
        int sort = 1;
        for (String image : imageList) {
            WsAlbumProductImportMedia media = new WsAlbumProductImportMedia();
            media.setShopId(shopId);
            media.setGoodsId(goodsId);
            media.setMediaType("image");
            media.setSourceUrl(image);
            media.setSortNo(sort++);
            media.setStatus(1);
            wsAlbumProductImportMediaMapper.insert(media);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    protected void replaceItemTags(String shopId, String goodsId, JsonNode item) {
        wsAlbumProductImportTagRelMapper.delete(new LambdaQueryWrapper<WsAlbumProductImportTagRel>()
                .eq(WsAlbumProductImportTagRel::getShopId, shopId)
                .eq(WsAlbumProductImportTagRel::getGoodsId, goodsId));

        JsonNode tags = item.path("tags");
        if (!tags.isArray()) {
            return;
        }

        Set<Long> seenTagIds = new HashSet<Long>();
        for (JsonNode tag : tags) {
            Long tagId = longValue(tag, "tagId");
            String tagName = textValue(tag, "tagName");
            if (tagId == null || seenTagIds.contains(tagId)) {
                continue;
            }
            seenTagIds.add(tagId);

            WsAlbumProductImportTagRel rel = new WsAlbumProductImportTagRel();
            rel.setShopId(shopId);
            rel.setGoodsId(goodsId);
            rel.setTagId(tagId);
            rel.setTagName(tagName);
            wsAlbumProductImportTagRelMapper.insert(rel);

            WsAlbumShopTag shopTag = wsAlbumShopTagMapper.selectOne(new LambdaQueryWrapper<WsAlbumShopTag>()
                    .eq(WsAlbumShopTag::getShopId, shopId)
                    .eq(WsAlbumShopTag::getTagId, tagId));
            if (shopTag == null) {
                shopTag = new WsAlbumShopTag();
                shopTag.setShopId(shopId);
                shopTag.setTagId(tagId);
                shopTag.setTagName(tagName);
                shopTag.setRawJson(toJson(tag));
                wsAlbumShopTagMapper.insert(shopTag);
            } else {
                shopTag.setTagName(tagName);
                shopTag.setRawJson(toJson(tag));
                wsAlbumShopTagMapper.updateById(shopTag);
            }
        }
    }

    private WsAlbumProductImport buildImportEntity(CrawlRuntime runtime,
                                                   String batchNo,
                                                   JsonNode item,
                                                   String shopId,
                                                   String goodsId,
                                                   Long incomingUpdateTime) {
        WsAlbumProductImport entity = new WsAlbumProductImport();
        entity.setShopId(shopId);
        entity.setGoodsId(goodsId);
        entity.setTitle(safeLimitText(textValue(item, "title"), IMPORT_TITLE_SAFE_MAX));
        entity.setSubTitle(safeLimitText(textValue(item, "subTitle"), IMPORT_SUBTITLE_SAFE_MAX));
        entity.setItemPrice(textValue(item, "itemPrice"));
        entity.setCurrencyCode("CNY");
        entity.setNewSendTime(longValue(item, "new_send_time"));
        entity.setTimeStamp(longValue(item, "time_stamp"));
        entity.setSourceUpdateTime(incomingUpdateTime);
        entity.setShopName(firstNonBlank(textValue(item, "shop_name"), runtime.shopName));
        entity.setSourceLink(textValue(item, "link"));
        entity.setTagGroupId(runtime.tagGroupId);
        entity.setBizExtJson(toJson(item.path("bizExt")));
        entity.setRawJson(toJson(item));

        String videoUrl = firstNonBlank(textValue(item, "videoUrl"), textValue(item, "videoURL"));
        entity.setHasVideo(StrUtil.isBlank(videoUrl) ? 0 : 1);
        entity.setVideoUrl(videoUrl);
        entity.setVideoThumbImg(textValue(item, "videoThumbImg"));

        List<String> imageUrls = extractImageUrls(item);
        if (!imageUrls.isEmpty()) {
            entity.setMainImageUrl(imageUrls.get(0));
            entity.setMediaCount(imageUrls.size());
        } else if (StrUtil.isNotBlank(videoUrl)) {
            entity.setMainImageUrl(entity.getVideoThumbImg());
            entity.setMediaCount(StrUtil.isBlank(entity.getVideoThumbImg()) ? 0 : 1);
        } else {
            entity.setMainImageUrl(null);
            entity.setMediaCount(0);
        }

        entity.setImportStatus(IMPORT_STATUS_PENDING);
        entity.setCrawlBatchNo(batchNo);
        entity.setLastCrawlTime(LocalDateTime.now());
        entity.setDeleted(0);
        if (entity.getFirstCrawlTime() == null) {
            entity.setFirstCrawlTime(LocalDateTime.now());
        }
        return entity;
    }

    private String safeLimitText(String value, int maxLen) {
        if (StrUtil.isBlank(value)) {
            return value;
        }
        String normalized = value.trim();
        if (normalized.length() <= maxLen) {
            return normalized;
        }
        return normalized.substring(0, maxLen);
    }

    private String buildPageSignature(JsonNode items) {
        if (items == null || !items.isArray() || items.size() == 0) {
            return null;
        }
        StringBuilder signature = new StringBuilder();
        signature.append(items.size()).append("|");
        int previewCount = Math.min(items.size(), 5);
        for (int i = 0; i < previewCount; i++) {
            JsonNode current = items.get(i);
            signature.append(firstNonBlank(textValue(current, "goods_id"), textValue(current, "selfGoodsId"), "-")).append(",");
        }
        JsonNode last = items.get(items.size() - 1);
        signature.append("|").append(firstNonBlank(textValue(last, "goods_id"), textValue(last, "selfGoodsId"), "-"));
        Long lastUpdate = firstNonNull(longValue(last, "update_time"), longValue(last, "time_stamp"));
        if (lastUpdate != null) {
            signature.append("|").append(lastUpdate);
        }
        return signature.toString();
    }

    private List<String> extractImageUrls(JsonNode item) {
        List<String> urls = new ArrayList<String>();

        JsonNode src = item.path("imgsSrc");
        if (src.isArray()) {
            for (JsonNode node : src) {
                String value = node.asText();
                if (StrUtil.isNotBlank(value)) {
                    urls.add(value.trim());
                }
            }
        }

        if (!urls.isEmpty()) {
            return urls;
        }

        JsonNode imgs = item.path("imgs");
        if (imgs.isArray()) {
            for (JsonNode node : imgs) {
                String value = node.asText();
                if (StrUtil.isNotBlank(value)) {
                    urls.add(stripQuery(value.trim()));
                }
            }
        }
        return urls;
    }

    private String stripQuery(String url) {
        int index = url.indexOf('?');
        if (index > 0) {
            return url.substring(0, index);
        }
        return url;
    }

    private WsAlbumShop upsertShopFromResult(JsonNode result, String albumId, String remark) {
        JsonNode targetAlbum = result.path("targetAlbum");
        if (targetAlbum.isMissingNode() || targetAlbum.isNull()) {
            throw new BusinessException("接口未返回 targetAlbum");
        }

        String shopId = firstNonBlank(textValue(targetAlbum, "id"), albumId);
        if (StrUtil.isBlank(shopId)) {
            throw new BusinessException("接口返回缺少店铺ID");
        }

        WsAlbumShop entity = wsAlbumShopMapper.selectOne(new LambdaQueryWrapper<WsAlbumShop>()
                .eq(WsAlbumShop::getShopId, shopId));
        if (entity == null) {
            entity = new WsAlbumShop();
            entity.setShopId(shopId);
            entity.setStatus(1);
        }

        entity.setAlbumId(firstNonBlank(albumId, textValue(targetAlbum, "id")));
        entity.setShopName(textValue(targetAlbum, "name"));
        entity.setIcon(textValue(targetAlbum, "icon"));
        entity.setBanner(textValue(targetAlbum, "banner"));
        entity.setQrcode(textValue(targetAlbum, "qrcode"));
        entity.setShareLink(firstNonBlank(textValue(targetAlbum, "shareLink"), textValue(targetAlbum, "shopLink"), textValue(result.path("share"), "link")));
        entity.setCover(textValue(targetAlbum, "cover"));
        entity.setPosterTitle(textValue(result.path("share"), "title"));
        entity.setTotalItemCount(intValue(targetAlbum.path("totalItemCount").asText()));
        entity.setIsHasTag(boolAsInt(targetAlbum.path("isHasTag").asBoolean(false)));
        entity.setIsFollowed(boolAsInt(targetAlbum.path("isFollowed").asBoolean(false)));
        entity.setHasVideo(boolAsInt(targetAlbum.path("hasVideo").asBoolean(false)));
        entity.setRawJson(toJson(targetAlbum));
        if (remark != null) {
            entity.setRemark(remark);
        }
        entity.setLastCrawlTime(LocalDateTime.now());

        if (entity.getId() == null) {
            wsAlbumShopMapper.insert(entity);
        } else {
            wsAlbumShopMapper.updateById(entity);
        }
        return entity;
    }

    private void saveCrawlRequestLog(String crawlBatchNo,
                                     Integer pageNo,
                                     String requestUrl,
                                     String requestParamsJson,
                                     Long requestPageTimestamp,
                                     Long responsePageTimestamp,
                                     Integer responseIsLoadMore,
                                     Integer fetchedCount,
                                     Integer httpStatus,
                                     Integer status,
                                     String errorMessage) {
        WsAlbumCrawlRequestLog requestLog = new WsAlbumCrawlRequestLog();
        requestLog.setCrawlBatchNo(crawlBatchNo);
        requestLog.setPageNo(pageNo);
        requestLog.setRequestUrl(requestUrl);
        requestLog.setRequestMethod("POST");
        requestLog.setRequestParamsJson(requestParamsJson);
        requestLog.setRequestPageTimestamp(requestPageTimestamp);
        requestLog.setResponsePageTimestamp(responsePageTimestamp);
        requestLog.setResponseIsLoadMore(responseIsLoadMore);
        requestLog.setFetchedCount(fetchedCount);
        requestLog.setHttpStatus(httpStatus);
        requestLog.setStatus(status);
        requestLog.setErrorMessage(StrUtil.sub(firstNonBlank(errorMessage, ""), 0, 1000));
        wsAlbumCrawlRequestLogMapper.insert(requestLog);
    }

    private Map<String, Object> buildRequestTraceParams(CrawlRuntime runtime, Long pageTimestamp) {
        Map<String, Object> map = runtime.toRequestParamsMap();
        map.put("timestamp", pageTimestamp);
        map.put("tagListBody", runtime.tagList == null ? Collections.emptyList() : runtime.tagList);
        return map;
    }

    private Map<String, Object> buildItemRequestTraceParams(CrawlRuntime runtime, String itemId) {
        Map<String, Object> map = runtime.toRequestParamsMap();
        map.put("requestType", "SPECIFIED_ITEM");
        map.put("itemId", itemId);
        map.put("mergeItemIds", runtime.mergeItemIds ? 1 : 0);
        return map;
    }

    private String buildCommodityViewUrl(CrawlRuntime runtime, String itemId) {
        String targetAlbumId = firstNonBlank(runtime.albumId, runtime.shopId);
        if (StrUtil.isBlank(targetAlbumId)) {
            throw new BusinessException("缺少目标店铺ID，无法按图文ID抓取");
        }
        if (StrUtil.isBlank(itemId)) {
            throw new BusinessException("图文ID为空");
        }
        try {
            return COMMODITY_VIEW_ENDPOINT
                    + "?targetAlbumId=" + URLEncoder.encode(targetAlbumId, "UTF-8")
                    + "&itemId=" + URLEncoder.encode(itemId, "UTF-8");
        } catch (Exception e) {
            return COMMODITY_VIEW_ENDPOINT + "?targetAlbumId=" + targetAlbumId + "&itemId=" + itemId;
        }
    }

    private FetchItemResult fetchCommodityItemWithMeta(String token, CrawlRuntime runtime, String itemId) {
        String url = buildCommodityViewUrl(runtime, itemId);
        HttpResponse response = HttpRequest.get(url)
                .header("Accept", "application/json, text/javascript, text/html, */*; q=0.01")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("x-wg-language", StrUtil.blankToDefault(runtime.xWgLanguage, "zh"))
                .header("Origin", "https://www.szwego.com")
                .header("Referer", "https://www.szwego.com/")
                .header("Cookie", "token=" + token)
                .timeout(30000)
                .execute();

        if (response.getStatus() >= 400) {
            throw new BusinessException("请求图文详情失败，HTTP=" + response.getStatus());
        }
        String body = StrUtil.blankToDefault(response.body(), "");
        JsonNode itemNode = extractCommodityItemNode(body, itemId);
        if (itemNode == null || itemNode.isMissingNode() || itemNode.isNull()) {
            throw new BusinessException("图文详情解析失败，itemId=" + itemId);
        }
        JsonNode normalized = normalizeCommodityItemNode(itemNode, runtime, itemId, url);
        return new FetchItemResult(normalized, response.getStatus(), url);
    }

    private JsonNode extractCommodityItemNode(String body, String itemId) {
        if (StrUtil.isBlank(body)) {
            return null;
        }

        JsonNode fromDirectJson = tryExtractCommodityFromJson(body, itemId);
        if (fromDirectJson != null) {
            return fromDirectJson;
        }

        String stateJson = extractJsonObjectByMarker(body, ITEM_STATE_MARKER_PATTERN);
        if (StrUtil.isBlank(stateJson)) {
            stateJson = extractJsonObjectByMarker(body, ITEM_NUXT_MARKER_PATTERN);
        }
        if (StrUtil.isBlank(stateJson)) {
            return null;
        }
        return tryExtractCommodityFromJson(stateJson, itemId);
    }

    private JsonNode tryExtractCommodityFromJson(String json, String itemId) {
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || root.isNull()) {
                return null;
            }

            JsonNode matched = findItemNodeByItemId(root, itemId, 0);
            if (matched != null) {
                return matched;
            }

            JsonNode fallback = findFirstLikelyItemNode(root, 0);
            if (fallback != null) {
                return fallback;
            }
        } catch (Exception ignore) {
            return null;
        }
        return null;
    }

    private JsonNode findItemNodeByItemId(JsonNode node, String itemId, int depth) {
        if (node == null || node.isNull() || depth > 16 || StrUtil.isBlank(itemId)) {
            return null;
        }
        if (node.isObject()) {
            String currentId = firstNonBlank(textValue(node, "goods_id"), textValue(node, "selfGoodsId"),
                    textValue(node, "itemId"), textValue(node, "id"), textValue(node, "goodsId"));
            if (StrUtil.isNotBlank(currentId) && itemId.equals(currentId) && isLikelyItemNode(node)) {
                return node;
            }
            java.util.Iterator<JsonNode> fields = node.elements();
            while (fields.hasNext()) {
                JsonNode child = fields.next();
                JsonNode found = findItemNodeByItemId(child, itemId, depth + 1);
                if (found != null) {
                    return found;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                JsonNode found = findItemNodeByItemId(child, itemId, depth + 1);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private JsonNode findFirstLikelyItemNode(JsonNode node, int depth) {
        if (node == null || node.isNull() || depth > 16) {
            return null;
        }
        if (node.isObject()) {
            if (isLikelyItemNode(node)) {
                return node;
            }
            java.util.Iterator<JsonNode> fields = node.elements();
            while (fields.hasNext()) {
                JsonNode child = fields.next();
                JsonNode found = findFirstLikelyItemNode(child, depth + 1);
                if (found != null) {
                    return found;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                JsonNode found = findFirstLikelyItemNode(child, depth + 1);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private boolean isLikelyItemNode(JsonNode node) {
        if (node == null || !node.isObject()) {
            return false;
        }
        boolean hasTitle = StrUtil.isNotBlank(textValue(node, "title"));
        boolean hasGoodsId = StrUtil.isNotBlank(firstNonBlank(textValue(node, "goods_id"), textValue(node, "itemId"), textValue(node, "id")));
        boolean hasMedia = node.path("imgs").isArray() || node.path("imgsSrc").isArray()
                || StrUtil.isNotBlank(textValue(node, "videoUrl")) || StrUtil.isNotBlank(textValue(node, "videoURL"));
        return hasGoodsId && (hasTitle || hasMedia);
    }

    private String extractJsonObjectByMarker(String body, Pattern markerPattern) {
        if (StrUtil.isBlank(body) || markerPattern == null) {
            return null;
        }
        Matcher marker = markerPattern.matcher(body);
        if (!marker.find()) {
            return null;
        }
        int markerEnd = marker.end();
        int start = body.indexOf('{', markerEnd);
        if (start < 0) {
            return null;
        }
        return extractBalancedJsonObject(body, start);
    }

    private String extractBalancedJsonObject(String text, int startIndex) {
        if (StrUtil.isBlank(text) || startIndex < 0 || startIndex >= text.length() || text.charAt(startIndex) != '{') {
            return null;
        }
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = startIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                    continue;
                }
                if (c == '\\') {
                    escape = true;
                    continue;
                }
                if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
                continue;
            }
            if (c == '{') {
                depth++;
                continue;
            }
            if (c == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(startIndex, i + 1);
                }
            }
        }
        return null;
    }

    private JsonNode normalizeCommodityItemNode(JsonNode source, CrawlRuntime runtime, String itemId, String requestUrl) {
        ObjectNode node = source != null && source.isObject()
                ? ((ObjectNode) source.deepCopy())
                : objectMapper.createObjectNode();
        String goodsId = firstNonBlank(textValue(source, "goods_id"), textValue(source, "selfGoodsId"),
                textValue(source, "itemId"), textValue(source, "id"), textValue(source, "goodsId"), itemId);
        node.put("goods_id", goodsId);
        node.put("shop_id", firstNonBlank(textValue(source, "shop_id"), runtime.shopId));
        node.put("shop_name", firstNonBlank(textValue(source, "shop_name"), runtime.shopName));
        node.put("link", firstNonBlank(textValue(source, "link"), requestUrl));

        Long updateTime = firstNonNull(longValue(source, "update_time"), longValue(source, "time_stamp"), System.currentTimeMillis());
        if (updateTime != null) {
            node.put("update_time", updateTime);
            node.put("time_stamp", updateTime);
        }
        return node;
    }

    private JsonNode buildMergedItemForSpecifiedIds(CrawlRuntime runtime, List<JsonNode> fetchedItems) {
        if (fetchedItems == null || fetchedItems.isEmpty()) {
            throw new BusinessException("指定图文抓取结果为空，无法合并");
        }
        ObjectNode base = fetchedItems.get(0) != null && fetchedItems.get(0).isObject()
                ? ((ObjectNode) fetchedItems.get(0).deepCopy())
                : objectMapper.createObjectNode();
        String mergedId = buildMergedGoodsId(runtime, fetchedItems);
        base.put("goods_id", mergedId);
        base.put("shop_id", runtime.shopId);
        base.put("shop_name", runtime.shopName);

        LinkedHashSet<String> titles = new LinkedHashSet<String>();
        LinkedHashSet<String> subTitles = new LinkedHashSet<String>();
        LinkedHashSet<String> images = new LinkedHashSet<String>();
        LinkedHashSet<String> sourceLinks = new LinkedHashSet<String>();
        Long maxUpdateTime = 0L;
        String price = "";
        String videoUrl = "";
        String videoThumb = "";
        ArrayNode mergedTags = objectMapper.createArrayNode();

        for (JsonNode item : fetchedItems) {
            if (item == null || item.isNull()) {
                continue;
            }
            String title = textValue(item, "title");
            if (StrUtil.isNotBlank(title)) {
                titles.add(title.trim());
            }
            String subTitle = textValue(item, "subTitle");
            if (StrUtil.isNotBlank(subTitle)) {
                subTitles.add(subTitle.trim());
            }
            if (StrUtil.isBlank(price)) {
                price = firstNonBlank(textValue(item, "itemPrice"), "");
            }
            if (StrUtil.isBlank(videoUrl)) {
                videoUrl = firstNonBlank(textValue(item, "videoUrl"), textValue(item, "videoURL"), "");
                videoThumb = firstNonBlank(textValue(item, "videoThumbImg"), "");
            }
            Long updateTime = firstNonNull(longValue(item, "update_time"), longValue(item, "time_stamp"), 0L);
            if (updateTime != null && updateTime > maxUpdateTime) {
                maxUpdateTime = updateTime;
            }
            List<String> urls = extractImageUrls(item);
            for (String url : urls) {
                if (StrUtil.isNotBlank(url)) {
                    images.add(url);
                }
            }
            String sourceLink = textValue(item, "link");
            if (StrUtil.isNotBlank(sourceLink)) {
                sourceLinks.add(sourceLink);
            }

            JsonNode tags = item.path("tags");
            if (tags.isArray()) {
                for (JsonNode tag : tags) {
                    mergedTags.add(tag.deepCopy());
                }
            }
        }

        if (!titles.isEmpty()) {
            base.put("title", StrUtil.sub(String.join(" / ", titles), 0, IMPORT_TITLE_SAFE_MAX));
        }
        if (!subTitles.isEmpty()) {
            base.put("subTitle", StrUtil.sub(String.join(" / ", subTitles), 0, IMPORT_SUBTITLE_SAFE_MAX));
        }
        if (StrUtil.isNotBlank(price)) {
            base.put("itemPrice", price);
        }
        if (maxUpdateTime > 0L) {
            base.put("update_time", maxUpdateTime);
            base.put("time_stamp", maxUpdateTime);
            base.put("new_send_time", maxUpdateTime);
        }

        ArrayNode imgsNode = objectMapper.createArrayNode();
        for (String url : images) {
            imgsNode.add(url);
        }
        if (imgsNode.size() > 0) {
            base.set("imgs", imgsNode);
            base.set("imgsSrc", imgsNode.deepCopy());
        }

        if (StrUtil.isNotBlank(videoUrl)) {
            base.put("videoUrl", videoUrl);
            base.put("videoThumbImg", videoThumb);
        }

        if (sourceLinks.isEmpty()) {
            base.put("link", buildCommodityViewUrl(runtime, runtime.itemIds.get(0)));
        } else {
            base.put("link", sourceLinks.iterator().next());
        }
        base.put("merged_item_ids", String.join(",", runtime.itemIds));
        if (mergedTags.size() > 0) {
            base.set("tags", mergedTags);
        }
        return base;
    }

    private String buildMergedGoodsId(CrawlRuntime runtime, List<JsonNode> fetchedItems) {
        List<String> ids = new ArrayList<String>();
        if (runtime.itemIds != null) {
            ids.addAll(runtime.itemIds);
        } else {
            for (JsonNode item : fetchedItems) {
                String id = firstNonBlank(textValue(item, "goods_id"), textValue(item, "itemId"), textValue(item, "id"));
                if (StrUtil.isNotBlank(id)) {
                    ids.add(id);
                }
            }
        }
        Collections.sort(ids);
        String raw = firstNonBlank(runtime.shopId, runtime.albumId, "") + "|" + String.join(",", ids);
        return "MERGE_" + md5Hex(raw).substring(0, 16).toUpperCase(Locale.ROOT);
    }

    private String md5Hex(String value) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] digest = md5.digest(firstNonBlank(value, "").getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                String hex = Integer.toHexString(b & 0xFF);
                if (hex.length() < 2) {
                    sb.append('0');
                }
                sb.append(hex);
            }
            return sb.toString();
        } catch (Exception e) {
            return IdUtil.fastSimpleUUID().replace("-", "");
        }
    }

    private JsonNode fetchAlbumPage(String token, CrawlRuntime runtime, Long pageTimestamp) {
        return fetchAlbumPageWithMeta(token, runtime, buildRequestUrl(runtime, pageTimestamp)).result;
    }

    private FetchPageResult fetchAlbumPageWithMeta(String token, CrawlRuntime runtime, String url) {

        HttpResponse response = HttpRequest.post(url)
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("x-wg-language", StrUtil.blankToDefault(runtime.xWgLanguage, "zh"))
                .header("Origin", "https://www.szwego.com")
                .header("Referer", "https://www.szwego.com/")
                .header("Cookie", "token=" + token)
                .form("tagList", toJson(runtime.tagList == null ? Collections.emptyList() : runtime.tagList))
                .timeout(30000)
                .execute();

        if (response.getStatus() >= 400) {
            throw new BusinessException("请求微商相册接口失败，HTTP=" + response.getStatus());
        }

        String body = response.body();
        try {
            JsonNode root = objectMapper.readTree(body);
            boolean success = root.path("success").asBoolean(false);
            int status = root.path("status").asInt(0);
            int errcode = root.path("errcode").asInt(0);
            if (!success || status != 0 || errcode != 0) {
                String msg = firstNonBlank(root.path("errmsg").asText(), root.path("message").asText(), "请求微商相册失败");
                throw new BusinessException(msg);
            }
            return new FetchPageResult(root.path("result"), response.getStatus());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("解析微商相册响应失败: " + e.getMessage());
        }
    }

    private String buildRequestUrl(CrawlRuntime runtime, Long pageTimestamp) {
        StringBuilder sb = new StringBuilder(wsAlbumProperties.getEndpoint());
        sb.append("?");
        appendQuery(sb, "albumId", runtime.albumId);
        appendQuery(sb, "searchValue", runtime.searchValue);
        appendQuery(sb, "searchImg", "");
        appendQuery(sb, "startDate", runtime.startDate);
        appendQuery(sb, "endDate", runtime.endDate);
        appendQuery(sb, "sourceId", "");
        appendQuery(sb, "requestDataType", "");
        appendQuery(sb, "transLang", runtime.transLang);
        appendQuery(sb, "tagGroupId", runtime.tagGroupId);
        if (pageTimestamp != null && pageTimestamp > 0) {
            appendQuery(sb, "timestamp", String.valueOf(pageTimestamp));
        }
        return sb.toString();
    }

    private void appendQuery(StringBuilder sb, String key, String value) {
        if (sb.charAt(sb.length() - 1) != '?') {
            sb.append('&');
        }
        sb.append(key).append('=');
        if (value == null) {
            return;
        }
        try {
            sb.append(URLEncoder.encode(value, "UTF-8"));
        } catch (Exception e) {
            sb.append(value);
        }
    }

    private WsAlbumConfig requireEnabledConfig() {
        WsAlbumConfig config = getEnabledConfig();
        if (config == null) {
            throw new BusinessException("请先在“抓取配置管理”启用有效 token");
        }
        if (StrUtil.isBlank(config.getTokenEnc())) {
            throw new BusinessException("抓取配置 token 为空");
        }
        return config;
    }

    private WsAlbumConfig getEnabledConfig() {
        WsAlbumConfig config = wsAlbumConfigMapper.selectOne(new LambdaQueryWrapper<WsAlbumConfig>()
                .eq(WsAlbumConfig::getEnabled, 1)
                .orderByDesc(WsAlbumConfig::getUpdateTime)
                .last("limit 1"));
        if (config != null) {
            return config;
        }
        return wsAlbumConfigMapper.selectOne(new LambdaQueryWrapper<WsAlbumConfig>()
                .orderByDesc(WsAlbumConfig::getUpdateTime)
                .last("limit 1"));
    }

    private String decryptToken(WsAlbumConfig config) {
        try {
            return WsAlbumTokenCryptoUtil.decrypt(config.getTokenEnc(), wsAlbumProperties.getTokenSecret());
        } catch (Exception e) {
            throw new BusinessException("token 解密失败，请重新配置");
        }
    }

    private WsAlbumConfigVO toConfigVO(WsAlbumConfig entity) {
        WsAlbumConfigVO vo = new WsAlbumConfigVO();
        BeanUtils.copyProperties(entity, vo);
        vo.setTokenMasked(WsAlbumTokenCryptoUtil.maskToken(decryptToken(entity)));
        return vo;
    }

    private CrawlRuntime buildCrawlRuntime(WsAlbumCrawlStartRequest request, WsAlbumConfig config) {
        CrawlRuntime runtime = new CrawlRuntime();
        runtime.shopId = StrUtil.trim(request.getShopId());
        runtime.albumId = StrUtil.trim(request.getAlbumId());
        runtime.crawlMode = StrUtil.blankToDefault(StrUtil.trim(request.getCrawlMode()), "FULL").toUpperCase(Locale.ROOT);
        runtime.startDate = StrUtil.blankToDefault(StrUtil.trim(request.getStartDate()), "");
        runtime.endDate = StrUtil.blankToDefault(StrUtil.trim(request.getEndDate()), "");
        runtime.searchValue = StrUtil.blankToDefault(StrUtil.trim(request.getSearchValue()), "");
        runtime.tagGroupId = StrUtil.blankToDefault(StrUtil.trim(request.getTagGroupId()), "");
        runtime.transLang = StrUtil.blankToDefault(config.getDefaultTransLang(), "en");
        runtime.xWgLanguage = StrUtil.blankToDefault(config.getDefaultXWgLang(), "zh");
        runtime.startTimestamp = request.getStartTimestamp();
        if (runtime.startTimestamp != null && runtime.startTimestamp <= 0L) {
            runtime.startTimestamp = null;
        }
        runtime.maxPages = request.getMaxPages() == null ? config.getDefaultMaxPages() : request.getMaxPages();
        if (runtime.maxPages <= 0) {
            runtime.maxPages = 20;
        }
        runtime.enableTimestampUpdate = request.getEnableTimestampUpdate() == null || request.getEnableTimestampUpdate() == 1;
        runtime.tagList = normalizeTagIds(request.getTagList());
        runtime.remark = request.getRemark();
        runtime.itemIds = normalizeRequestedItemIds(request.getItemIds());
        runtime.mergeItemIds = request.getMergeItemIds() != null && request.getMergeItemIds() == 1;
        if (runtime.mergeItemIds && runtime.itemIds.size() <= 1) {
            runtime.mergeItemIds = false;
        }
        if (!runtime.itemIds.isEmpty() && StrUtil.isBlank(runtime.shopId)) {
            throw new BusinessException("指定图文ID抓取必须选择来源店铺");
        }

        if ("SYNC".equals(runtime.crawlMode)) {
            runtime.stopOnDuplicate = true;
            runtime.pageTimestampStart = runtime.startTimestamp == null ? System.currentTimeMillis() : runtime.startTimestamp;
        } else if ("INCREMENTAL".equals(runtime.crawlMode)) {
            runtime.stopOnDuplicate = false;
            runtime.pageTimestampStart = runtime.startTimestamp;
        } else {
            runtime.stopOnDuplicate = false;
        }
        return runtime;
    }

    private List<String> normalizeRequestedItemIds(List<String> itemIds) {
        LinkedHashSet<String> values = new LinkedHashSet<String>();
        if (itemIds == null) {
            return new ArrayList<String>();
        }
        for (String raw : itemIds) {
            if (StrUtil.isBlank(raw)) {
                continue;
            }
            String value = raw.trim();
            if (value.length() > 128) {
                value = value.substring(0, 128);
            }
            values.add(value);
            if (values.size() >= 200) {
                break;
            }
        }
        return new ArrayList<String>(values);
    }

    private List<Long> normalizeTagIds(List<Long> tagList) {
        List<Long> normalized = new ArrayList<Long>();
        if (tagList == null || tagList.isEmpty()) {
            return normalized;
        }
        Set<Long> seen = new LinkedHashSet<Long>();
        for (Long tagId : tagList) {
            if (tagId == null || tagId <= 0 || seen.contains(tagId)) {
                continue;
            }
            seen.add(tagId);
            normalized.add(tagId);
            if (normalized.size() >= 200) {
                break;
            }
        }
        return normalized;
    }

    private int normalizedTagCount(List<Long> tagList) {
        return normalizeTagIds(tagList).size();
    }

    private String buildTagScopeKey(List<Long> tagList) {
        List<Long> normalized = normalizeTagIds(tagList);
        if (normalized.isEmpty()) {
            return "";
        }
        List<Long> sorted = new ArrayList<Long>(normalized);
        Collections.sort(sorted);
        return sorted.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    private List<Long> parseTagIdsJson(String json) {
        List<Long> ids = new ArrayList<Long>();
        if (StrUtil.isBlank(json)) {
            return ids;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (!node.isArray()) {
                return ids;
            }
            for (JsonNode item : node) {
                if (item == null || item.isNull()) {
                    continue;
                }
                Long value = longValue(item.asText());
                if (value != null && value > 0) {
                    ids.add(value);
                }
            }
        } catch (Exception ignore) {
            return ids;
        }
        return normalizeTagIds(ids);
    }

    private Long loadLatestIncrementalTimestamp(CrawlRuntime runtime) {
        if (runtime == null || StrUtil.isBlank(runtime.shopId)) {
            return null;
        }
        String tagScopeKey = buildTagScopeKey(runtime.tagList);
        List<WsAlbumCrawlLog> recentLogs = wsAlbumCrawlLogMapper.selectList(new LambdaQueryWrapper<WsAlbumCrawlLog>()
                .eq(WsAlbumCrawlLog::getShopId, runtime.shopId)
                .eq(WsAlbumCrawlLog::getStatus, CRAWL_STATUS_SUCCESS)
                .isNotNull(WsAlbumCrawlLog::getPageTimestampEnd)
                .orderByDesc(WsAlbumCrawlLog::getId)
                .last("limit 200"));
        for (WsAlbumCrawlLog recentLog : recentLogs) {
            if (tagScopeKey.equals(buildTagScopeKey(parseTagIdsJson(recentLog.getTagListJson())))
                    && recentLog.getPageTimestampEnd() != null
                    && recentLog.getPageTimestampEnd() > 0) {
                return recentLog.getPageTimestampEnd();
            }
        }

        List<Long> normalizedTags = normalizeTagIds(runtime.tagList);
        if (normalizedTags.size() == 1) {
            try {
                Long latestTaggedTs = jdbcTemplate.queryForObject(
                        "SELECT MAX(i.source_update_time) " +
                                "FROM ws_album_product_import i " +
                                "JOIN ws_album_product_import_tag_rel r ON r.shop_id = i.shop_id AND r.goods_id = i.goods_id " +
                                "WHERE i.shop_id = ? AND i.deleted = 0 AND r.tag_id = ?",
                        Long.class,
                        runtime.shopId,
                        normalizedTags.get(0)
                );
                if (latestTaggedTs != null && latestTaggedTs > 0) {
                    return latestTaggedTs;
                }
            } catch (Exception ignore) {
                // 标签维度查询失败时，返回 null 让本次按该标签全量补抓。
            }
            return null;
        }

        WsAlbumProductImport latest = wsAlbumProductImportMapper.selectOne(new LambdaQueryWrapper<WsAlbumProductImport>()
                .eq(WsAlbumProductImport::getShopId, runtime.shopId)
                .eq(WsAlbumProductImport::getDeleted, 0)
                .orderByDesc(WsAlbumProductImport::getSourceUpdateTime)
                .last("limit 1"));
        if (latest == null) {
            return null;
        }
        return latest.getSourceUpdateTime();
    }

    private WsAlbumShop resolveTargetShop(CrawlRuntime runtime) {
        WsAlbumShop shop = null;
        if (StrUtil.isNotBlank(runtime.shopId)) {
            shop = wsAlbumShopMapper.selectOne(new LambdaQueryWrapper<WsAlbumShop>()
                    .eq(WsAlbumShop::getShopId, runtime.shopId));
            if (shop == null) {
                throw new BusinessException("指定店铺不存在");
            }
            runtime.albumId = StrUtil.blankToDefault(runtime.albumId, firstNonBlank(shop.getAlbumId(), shop.getShopId()));
        }
        if (StrUtil.isBlank(runtime.albumId)) {
            throw new BusinessException("请提供 shopId 或 albumId");
        }

        if (shop == null) {
            shop = wsAlbumShopMapper.selectOne(new LambdaQueryWrapper<WsAlbumShop>()
                    .eq(WsAlbumShop::getAlbumId, runtime.albumId)
                    .or().eq(WsAlbumShop::getShopId, runtime.albumId)
                    .last("limit 1"));
            if (shop == null) {
                shop = new WsAlbumShop();
                shop.setShopId(runtime.albumId);
                shop.setAlbumId(runtime.albumId);
                shop.setShopName(runtime.albumId);
                shop.setStatus(1);
                wsAlbumShopMapper.insert(shop);
            }
        }
        return shop;
    }

    private Map<String, List<String>> loadTagMap(Set<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return new HashMap<String, List<String>>();
        }
        Set<String> shopIds = new HashSet<String>();
        Set<String> goodsIds = new HashSet<String>();
        for (String key : keys) {
            String[] parts = key.split("\\|", 2);
            if (parts.length == 2) {
                shopIds.add(parts[0]);
                goodsIds.add(parts[1]);
            }
        }
        if (shopIds.isEmpty() || goodsIds.isEmpty()) {
            return new HashMap<String, List<String>>();
        }

        List<WsAlbumProductImportTagRel> rels = wsAlbumProductImportTagRelMapper.selectList(new LambdaQueryWrapper<WsAlbumProductImportTagRel>()
                .in(WsAlbumProductImportTagRel::getShopId, shopIds)
                .in(WsAlbumProductImportTagRel::getGoodsId, goodsIds));

        Map<String, List<String>> map = new HashMap<String, List<String>>();
        for (WsAlbumProductImportTagRel rel : rels) {
            String key = buildGoodsKey(rel.getShopId(), rel.getGoodsId());
            if (!keys.contains(key)) {
                continue;
            }
            List<String> list = map.computeIfAbsent(key, k -> new ArrayList<String>());
            if (StrUtil.isNotBlank(rel.getTagName())) {
                list.add(rel.getTagName());
            }
        }
        return map;
    }

    private String buildGoodsKey(String shopId, String goodsId) {
        return firstNonBlank(shopId, "") + "|" + firstNonBlank(goodsId, "");
    }

    private String buildBatchNo(String prefix) {
        return prefix + DateUtil.format(new Date(), "yyyyMMddHHmmss") + IdUtil.fastSimpleUUID().substring(0, 6).toUpperCase(Locale.ROOT);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private String textValue(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return StrUtil.isBlank(text) ? null : text;
    }

    private Long longValue(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        try {
            String text = value.asText();
            if (StrUtil.isBlank(text)) {
                return null;
            }
            return Long.valueOf(text);
        } catch (Exception e) {
            return null;
        }
    }

    private Integer intValue(String value) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (Exception e) {
            return null;
        }
    }

    private Integer intValue(Object value) {
        if (value == null) {
            return null;
        }
        return intValue(String.valueOf(value));
    }

    private Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        try {
            String text = String.valueOf(value);
            if (StrUtil.isBlank(text)) {
                return null;
            }
            return Long.valueOf(text);
        } catch (Exception e) {
            return null;
        }
    }

    private int boolAsInt(boolean value) {
        return value ? 1 : 0;
    }

    private void appendError(StringBuilder builder, String text) {
        if (StrUtil.isBlank(text)) {
            return;
        }
        if (builder.length() > 0) {
            builder.append("; ");
        }
        builder.append(text);
    }

    private boolean isTrue(Integer value) {
        return value != null && value == 1;
    }

    private boolean isCrawlStopRequested(String batchNo) {
        return StrUtil.isNotBlank(batchNo) && Boolean.TRUE.equals(crawlStopFlagMap.get(batchNo));
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StrUtil.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        if (values == null) {
            return null;
        }
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private enum ProcessOutcome {
        INSERTED,
        UPDATED,
        DUPLICATED,
        FAILED
    }

    private static class DetectContentSource {
        private final String detectTitle;
        private final String detectDescription;

        private DetectContentSource(String detectTitle, String detectDescription) {
            this.detectTitle = detectTitle;
            this.detectDescription = detectDescription;
        }
    }

    private static class PriceDetectConfig {
        private String sourcePriority;
        private String matchMode;
        private String priceTransformType;
        private Integer transformDigits;
        private boolean detectInTitle;
        private boolean detectInSubTitle;
        private boolean stripTokens;
        private List<String> priceRegexList;
        private List<Pattern> customDetectPatterns;
        private List<Pattern> customTokenPatterns;

        private static PriceDetectConfig defaultConfig() {
            PriceDetectConfig config = new PriceDetectConfig();
            config.sourcePriority = PRICE_PRIORITY_ITEM_FIRST;
            config.matchMode = PRICE_MATCH_FIRST_HIT;
            config.priceTransformType = PRICE_TRANSFORM_NONE;
            config.transformDigits = 4;
            config.detectInTitle = true;
            config.detectInSubTitle = true;
            config.stripTokens = true;
            config.priceRegexList = new ArrayList<String>();
            config.customDetectPatterns = new ArrayList<Pattern>();
            config.customTokenPatterns = new ArrayList<Pattern>();
            return config;
        }
    }

    private static class NormalizedImportContent {
        private final String title;
        private final String description;
        private final java.math.BigDecimal detectedPrice;
        private final List<String> codeTags;

        private NormalizedImportContent(String title, String description, java.math.BigDecimal detectedPrice, List<String> codeTags) {
            this.title = title;
            this.description = description;
            this.detectedPrice = detectedPrice;
            this.codeTags = codeTags == null ? new ArrayList<String>() : codeTags;
        }
    }

    private static class LocalizedProductContent {
        private final String zhTitle;
        private final String zhDescription;
        private final String enTitle;
        private final String enDescription;

        private LocalizedProductContent(String zhTitle, String zhDescription, String enTitle, String enDescription) {
            this.zhTitle = StrUtil.trimToEmpty(zhTitle);
            this.zhDescription = StrUtil.trimToEmpty(zhDescription);
            this.enTitle = StrUtil.trimToEmpty(enTitle);
            this.enDescription = StrUtil.trimToEmpty(enDescription);
        }
    }

    private static class UploadResult {
        private final String ossKey;
        private final String ossUrl;

        private UploadResult(String ossKey, String ossUrl) {
            this.ossKey = ossKey;
            this.ossUrl = ossUrl;
        }
    }

    private static class FetchPageResult {
        private final JsonNode result;
        private final Integer httpStatus;

        private FetchPageResult(JsonNode result, Integer httpStatus) {
            this.result = result;
            this.httpStatus = httpStatus;
        }
    }

    private static class FetchItemResult {
        private final JsonNode item;
        private final Integer httpStatus;
        private final String requestUrl;

        private FetchItemResult(JsonNode item, Integer httpStatus, String requestUrl) {
            this.item = item;
            this.httpStatus = httpStatus;
            this.requestUrl = requestUrl;
        }
    }

    private static class CrawlRuntime {
        private String shopId;
        private String shopName;
        private String albumId;
        private String crawlMode;
        private String startDate;
        private String endDate;
        private String searchValue;
        private String tagGroupId;
        private List<Long> tagList;
        private String transLang;
        private String xWgLanguage;
        private int maxPages;
        private boolean enableTimestampUpdate;
        private boolean stopOnDuplicate;
        private Long startTimestamp;
        private Long pageTimestampStart;
        private String remark;
        private List<String> itemIds;
        private boolean mergeItemIds;

        private CrawlRuntime copy() {
            CrawlRuntime copy = new CrawlRuntime();
            copy.shopId = shopId;
            copy.shopName = shopName;
            copy.albumId = albumId;
            copy.crawlMode = crawlMode;
            copy.startDate = startDate;
            copy.endDate = endDate;
            copy.searchValue = searchValue;
            copy.tagGroupId = tagGroupId;
            copy.tagList = tagList == null ? new ArrayList<Long>() : new ArrayList<Long>(tagList);
            copy.transLang = transLang;
            copy.xWgLanguage = xWgLanguage;
            copy.maxPages = maxPages;
            copy.enableTimestampUpdate = enableTimestampUpdate;
            copy.stopOnDuplicate = stopOnDuplicate;
            copy.startTimestamp = startTimestamp;
            copy.pageTimestampStart = pageTimestampStart;
            copy.remark = remark;
            copy.itemIds = itemIds == null ? new ArrayList<String>() : new ArrayList<String>(itemIds);
            copy.mergeItemIds = mergeItemIds;
            return copy;
        }

        private Map<String, Object> toRequestParamsMap() {
            Map<String, Object> map = new HashMap<String, Object>();
            map.put("shopId", shopId);
            map.put("shopName", shopName);
            map.put("albumId", albumId);
            map.put("crawlMode", crawlMode);
            map.put("startDate", startDate);
            map.put("endDate", endDate);
            map.put("searchValue", searchValue);
            map.put("tagGroupId", tagGroupId);
            map.put("tagList", tagList);
            map.put("transLang", transLang);
            map.put("xWgLanguage", xWgLanguage);
            map.put("maxPages", maxPages);
            map.put("enableTimestampUpdate", enableTimestampUpdate ? 1 : 0);
            map.put("stopOnDuplicate", stopOnDuplicate ? 1 : 0);
            map.put("startTimestamp", startTimestamp);
            map.put("pageTimestampStart", pageTimestampStart);
            map.put("itemIds", itemIds);
            map.put("mergeItemIds", mergeItemIds ? 1 : 0);
            return map;
        }
    }

    private static class CrawlExecutionStats {
        private int pageCount;
        private int fetched;
        private int inserted;
        private int updated;
        private int duplicated;
        private int failed;
        private boolean stoppedByUser;
        private boolean stoppedByDuplicate;
        private Long lastPageTimestamp;
    }
}
