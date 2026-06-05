package com.szwego.gallery.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.szwego.gallery.common.BusinessException;
import com.szwego.gallery.config.CurrencyRateProperties;
import com.szwego.gallery.domain.CurrencyRate;
import com.szwego.gallery.dto.CurrencyRateSnapshotVO;
import com.szwego.gallery.mapper.CurrencyRateMapper;
import com.szwego.gallery.service.CurrencyRateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CurrencyRateServiceImpl implements CurrencyRateService {

    private static final Map<String, BigDecimal> FALLBACK_RATES = new LinkedHashMap<String, BigDecimal>();

    static {
        FALLBACK_RATES.put("CNY", BigDecimal.ONE);
        FALLBACK_RATES.put("USD", new BigDecimal("0.14"));
        FALLBACK_RATES.put("EUR", new BigDecimal("0.13"));
        FALLBACK_RATES.put("GBP", new BigDecimal("0.11"));
        FALLBACK_RATES.put("JPY", new BigDecimal("22.00"));
    }

    private final CurrencyRateMapper currencyRateMapper;
    private final CurrencyRateProperties currencyRateProperties;
    private final ObjectMapper objectMapper;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void init() {
        ensureTable();
        CurrencyRateSnapshotVO current = loadLatestSnapshot();
        if (current == null || current.getRates() == null || current.getRates().isEmpty()) {
            try {
                refreshLatestRates();
            } catch (Exception ex) {
                log.warn("初始化汇率失败: {}", ex.getMessage());
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public CurrencyRateSnapshotVO getLatestSnapshot() {
        CurrencyRateSnapshotVO snapshot = loadLatestSnapshot();
        if (snapshot != null && snapshot.getRates() != null && !snapshot.getRates().isEmpty()) {
            return snapshot;
        }
        try {
            refreshLatestRates();
            snapshot = loadLatestSnapshot();
            if (snapshot != null && snapshot.getRates() != null && !snapshot.getRates().isEmpty()) {
                return snapshot;
            }
        } catch (Exception ex) {
            log.warn("获取汇率时在线刷新失败: {}", ex.getMessage());
        }
        return buildFallbackSnapshot();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public synchronized void refreshLatestRates() {
        List<String> supported = normalizedSupportedCurrencies();
        String baseCurrency = normalizeCurrency(currencyRateProperties.getBaseCurrency());
        List<String> quotes = new ArrayList<String>();
        for (String currency : supported) {
            if (!currency.equals(baseCurrency)) {
                quotes.add(currency);
            }
        }
        if (quotes.isEmpty()) {
            throw new BusinessException("汇率币种配置为空");
        }

        String url = buildApiUrl(baseCurrency, quotes);
        HttpResponse response = HttpRequest.get(url)
                .timeout(resolveTimeout())
                .execute();
        if (response.getStatus() != 200) {
            throw new BusinessException("汇率接口返回异常HTTP状态: " + response.getStatus());
        }
        String body = response.body();
        if (StrUtil.isBlank(body)) {
            throw new BusinessException("汇率接口返回为空");
        }

        Map<String, BigDecimal> rates = parseRatesFromResponse(body, baseCurrency, supported);
        LocalDate rateDate = parseRateDateFromResponse(body);
        if (rateDate == null) {
            rateDate = LocalDate.now(resolveZoneId());
        }
        rates.put(baseCurrency, BigDecimal.ONE);

        for (String currency : supported) {
            BigDecimal rate = rates.get(currency);
            if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            upsertRate(baseCurrency, currency, rateDate, rate);
        }
    }

    @Scheduled(cron = "${app.currency.refresh-cron:0 10 3 * * *}", zone = "${app.currency.zone:Asia/Shanghai}")
    public void refreshLatestRatesDaily() {
        try {
            refreshLatestRates();
            log.info("每日汇率更新完成");
        } catch (Exception ex) {
            log.warn("每日汇率更新失败: {}", ex.getMessage());
        }
    }

    private void upsertRate(String baseCurrency, String targetCurrency, LocalDate rateDate, BigDecimal rate) {
        CurrencyRate existing = currencyRateMapper.selectOne(new LambdaQueryWrapper<CurrencyRate>()
                .eq(CurrencyRate::getBaseCurrency, baseCurrency)
                .eq(CurrencyRate::getTargetCurrency, targetCurrency)
                .eq(CurrencyRate::getRateDate, rateDate)
                .last("limit 1"));
        if (existing == null) {
            CurrencyRate row = new CurrencyRate();
            row.setBaseCurrency(baseCurrency);
            row.setTargetCurrency(targetCurrency);
            row.setRate(rate);
            row.setRateDate(rateDate);
            row.setSource(currencyRateProperties.getSourceName());
            currencyRateMapper.insert(row);
            return;
        }
        existing.setRate(rate);
        existing.setSource(currencyRateProperties.getSourceName());
        currencyRateMapper.updateById(existing);
    }

    private CurrencyRateSnapshotVO loadLatestSnapshot() {
        String baseCurrency = normalizeCurrency(currencyRateProperties.getBaseCurrency());
        CurrencyRate latest = currencyRateMapper.selectOne(new LambdaQueryWrapper<CurrencyRate>()
                .eq(CurrencyRate::getBaseCurrency, baseCurrency)
                .orderByDesc(CurrencyRate::getRateDate)
                .orderByDesc(CurrencyRate::getUpdateTime)
                .last("limit 1"));
        if (latest == null || latest.getRateDate() == null) {
            return null;
        }
        List<String> supported = normalizedSupportedCurrencies();
        List<CurrencyRate> rows = currencyRateMapper.selectList(new LambdaQueryWrapper<CurrencyRate>()
                .eq(CurrencyRate::getBaseCurrency, baseCurrency)
                .eq(CurrencyRate::getRateDate, latest.getRateDate())
                .in(CurrencyRate::getTargetCurrency, supported)
                .orderByAsc(CurrencyRate::getTargetCurrency));
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        Map<String, BigDecimal> rates = new LinkedHashMap<String, BigDecimal>();
        for (CurrencyRate row : rows) {
            if (row == null || StrUtil.isBlank(row.getTargetCurrency()) || row.getRate() == null) {
                continue;
            }
            rates.put(normalizeCurrency(row.getTargetCurrency()), row.getRate());
        }
        rates.put(baseCurrency, BigDecimal.ONE);
        for (String currency : supported) {
            if (!rates.containsKey(currency) && FALLBACK_RATES.containsKey(currency)) {
                rates.put(currency, FALLBACK_RATES.get(currency));
            }
        }
        LocalDateTime updatedAt = rows.stream()
                .filter(item -> item != null && item.getUpdateTime() != null)
                .map(CurrencyRate::getUpdateTime)
                .max(Comparator.naturalOrder())
                .orElse(null);

        CurrencyRateSnapshotVO vo = new CurrencyRateSnapshotVO();
        vo.setBaseCurrency(baseCurrency);
        vo.setRateDate(latest.getRateDate());
        vo.setUpdatedAt(updatedAt);
        vo.setSupportedCurrencies(supported);
        vo.setRates(rates);
        return vo;
    }

    private CurrencyRateSnapshotVO buildFallbackSnapshot() {
        List<String> supported = normalizedSupportedCurrencies();
        Map<String, BigDecimal> rates = new LinkedHashMap<String, BigDecimal>();
        for (String currency : supported) {
            BigDecimal rate = FALLBACK_RATES.get(currency);
            if (rate != null) {
                rates.put(currency, rate);
            }
        }
        String baseCurrency = normalizeCurrency(currencyRateProperties.getBaseCurrency());
        rates.put(baseCurrency, BigDecimal.ONE);

        CurrencyRateSnapshotVO vo = new CurrencyRateSnapshotVO();
        vo.setBaseCurrency(baseCurrency);
        vo.setRateDate(LocalDate.now(resolveZoneId()));
        vo.setUpdatedAt(LocalDateTime.now(resolveZoneId()));
        vo.setSupportedCurrencies(supported);
        vo.setRates(rates);
        return vo;
    }

    private Map<String, BigDecimal> parseRatesFromResponse(String body, String baseCurrency, List<String> supported) {
        try {
            JsonNode root = objectMapper.readTree(body);
            if (!root.isArray()) {
                throw new BusinessException("汇率接口格式不符合预期");
            }
            Set<String> expected = new LinkedHashSet<String>(supported);
            expected.remove(baseCurrency);
            Map<String, BigDecimal> rates = new LinkedHashMap<String, BigDecimal>();
            for (JsonNode node : root) {
                if (node == null || !node.isObject()) {
                    continue;
                }
                String base = normalizeCurrency(node.path("base").asText(""));
                String quote = normalizeCurrency(node.path("quote").asText(""));
                if (!baseCurrency.equals(base)) {
                    continue;
                }
                if (!expected.contains(quote)) {
                    continue;
                }
                BigDecimal rate = parseBigDecimal(node.path("rate").asText(""));
                if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                rates.put(quote, rate);
            }
            return rates;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("解析汇率接口响应失败: " + ex.getMessage());
        }
    }

    private LocalDate parseRateDateFromResponse(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            if (!root.isArray() || root.isEmpty()) {
                return null;
            }
            String dateText = root.get(0).path("date").asText("");
            if (StrUtil.isBlank(dateText)) {
                return null;
            }
            return LocalDate.parse(dateText);
        } catch (Exception ex) {
            return null;
        }
    }

    private String buildApiUrl(String baseCurrency, List<String> quotes) {
        String endpoint = StrUtil.trimToEmpty(currencyRateProperties.getApiUrl());
        if (endpoint.endsWith("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }
        return endpoint + "?base=" + baseCurrency + "&quotes=" + String.join(",", quotes);
    }

    private int resolveTimeout() {
        Integer timeout = currencyRateProperties.getTimeoutMs();
        if (timeout == null || timeout < 3000) {
            return 10000;
        }
        return timeout;
    }

    private List<String> normalizedSupportedCurrencies() {
        LinkedHashSet<String> set = new LinkedHashSet<String>();
        List<String> configured = currencyRateProperties.getSupportedCurrencies();
        if (configured != null) {
            for (String currency : configured) {
                String normalized = normalizeCurrency(currency);
                if (StrUtil.isNotBlank(normalized)) {
                    set.add(normalized);
                }
            }
        }
        String baseCurrency = normalizeCurrency(currencyRateProperties.getBaseCurrency());
        set.add(baseCurrency);
        if (set.isEmpty()) {
            set.add("USD");
            set.add("EUR");
            set.add("GBP");
            set.add("JPY");
            set.add("CNY");
        }
        return new ArrayList<String>(set);
    }

    private String normalizeCurrency(String currency) {
        return StrUtil.trimToEmpty(currency).toUpperCase(Locale.ROOT);
    }

    private BigDecimal parseBigDecimal(String text) {
        if (StrUtil.isBlank(text)) {
            return null;
        }
        try {
            return new BigDecimal(text.trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private ZoneId resolveZoneId() {
        String zone = StrUtil.blankToDefault(currencyRateProperties.getZone(), "Asia/Shanghai");
        try {
            return ZoneId.of(zone);
        } catch (Exception ex) {
            return ZoneId.of("Asia/Shanghai");
        }
    }

    private void ensureTable() {
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS currency_rate ("
                        + "id BIGINT NOT NULL,"
                        + "base_currency VARCHAR(16) NOT NULL,"
                        + "target_currency VARCHAR(16) NOT NULL,"
                        + "rate DECIMAL(20,8) NOT NULL,"
                        + "rate_date DATE NOT NULL,"
                        + "source VARCHAR(64) DEFAULT NULL,"
                        + "create_time DATETIME DEFAULT CURRENT_TIMESTAMP,"
                        + "update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                        + "PRIMARY KEY (id),"
                        + "UNIQUE KEY uk_base_target_date (base_currency, target_currency, rate_date),"
                        + "KEY idx_base_date (base_currency, rate_date)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
        );
    }
}
