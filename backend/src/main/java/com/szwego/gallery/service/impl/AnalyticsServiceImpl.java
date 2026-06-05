package com.szwego.gallery.service.impl;

import com.szwego.gallery.config.AnalyticsProperties;
import com.szwego.gallery.dto.AnalyticsDailyTrendVO;
import com.szwego.gallery.dto.AnalyticsOverviewVO;
import com.szwego.gallery.dto.AnalyticsPageEventRequest;
import com.szwego.gallery.dto.AnalyticsTopPageVO;
import com.szwego.gallery.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsServiceImpl implements AnalyticsService {

    private static final String PAGE_ALL = "__ALL__";
    private static final int MAX_PAGE_PATH_LEN = 255;
    private static final int MAX_PAGE_TITLE_LEN = 255;
    private static final int MAX_REFERRER_LEN = 1024;
    private static final int MAX_LANG_LEN = 32;
    private static final int MAX_TIMEZONE_LEN = 64;
    private static final int MAX_VISITOR_LEN = 64;
    private static final int MAX_SESSION_LEN = 64;
    private static final int MAX_IP_HASH_LEN = 64;
    private static final int MAX_UA_LEN = 512;

    private final JdbcTemplate jdbcTemplate;
    private final AnalyticsProperties analyticsProperties;
    @Qualifier("analyticsExecutor")
    private final ThreadPoolTaskExecutor analyticsExecutor;

    private final Set<String> ensuredMonthTables = ConcurrentHashMap.newKeySet();

    @PostConstruct
    public void init() {
        ensureSummaryTables();
    }

    @Override
    public void trackPageView(AnalyticsPageEventRequest request, HttpServletRequest httpServletRequest) {
        if (!analyticsProperties.isEnabled()) {
            return;
        }
        analyticsExecutor.execute(() -> safeSaveEvent("PAGE_VIEW", request, httpServletRequest));
    }

    @Override
    public void trackPageLeave(AnalyticsPageEventRequest request, HttpServletRequest httpServletRequest) {
        if (!analyticsProperties.isEnabled()) {
            return;
        }
        analyticsExecutor.execute(() -> safeSaveEvent("PAGE_LEAVE", request, httpServletRequest));
    }

    @Override
    public AnalyticsOverviewVO overview(Integer days, Integer topN) {
        int rangeDays = normalizeRangeDays(days);
        int topCount = normalizeTopN(topN);
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(rangeDays - 1L);

        AnalyticsOverviewVO vo = new AnalyticsOverviewVO();
        try {
            List<AnalyticsDailyTrendVO> trend = buildTrend(startDate, endDate);
            vo.setTrend(trend);
            vo.setTopPages(fetchTopPages(startDate, endDate, topCount));

            long totalPv = 0L;
            long totalStaySeconds = 0L;
            long totalLeaveCount = 0L;
            for (AnalyticsDailyTrendVO row : trend) {
                totalPv += row.getPv();
            }
            List<Map<String, Object>> stayRows = jdbcTemplate.queryForList(
                    "SELECT IFNULL(SUM(total_stay_seconds), 0) AS totalStay, IFNULL(SUM(leave_count), 0) AS totalLeave "
                            + "FROM analytics_page_daily_stat WHERE page_path = ? AND stat_date BETWEEN ? AND ?",
                    PAGE_ALL, startDate, endDate
            );
            if (!stayRows.isEmpty()) {
                Object totalStayObj = stayRows.get(0).get("totalStay");
                Object totalLeaveObj = stayRows.get(0).get("totalLeave");
                totalStaySeconds = toLong(totalStayObj);
                totalLeaveCount = toLong(totalLeaveObj);
            }
            vo.setPv(totalPv);
            vo.setAvgStaySeconds(totalLeaveCount <= 0 ? 0L : Math.round((double) totalStaySeconds / (double) totalLeaveCount));

            Long uv = jdbcTemplate.queryForObject(
                    "SELECT COUNT(DISTINCT visitor_hash) FROM analytics_page_daily_visitor FORCE INDEX (idx_page_date_visitor) "
                            + "WHERE page_path = ? AND stat_date BETWEEN ? AND ?",
                    Long.class,
                    PAGE_ALL, startDate, endDate
            );
            vo.setUv(uv == null ? 0L : uv);
        } catch (DataAccessException ex) {
            log.warn("analytics overview query failed: {}", ex.getMessage());
        }
        return vo;
    }

    private void safeSaveEvent(String eventType, AnalyticsPageEventRequest request, HttpServletRequest httpServletRequest) {
        try {
            saveEvent(eventType, request, httpServletRequest);
        } catch (Exception ex) {
            log.warn("analytics save event failed, type={}, msg={}", eventType, ex.getMessage());
        }
    }

    private void saveEvent(String eventType, AnalyticsPageEventRequest request, HttpServletRequest httpServletRequest) {
        AnalyticsPageEventRequest payload = request == null ? new AnalyticsPageEventRequest() : request;
        LocalDateTime eventTime = resolveEventTime(payload.getOccurredAt());
        LocalDate eventDate = eventTime.toLocalDate();

        String pagePath = truncate(defaultIfBlank(payload.getPagePath(), "/"), MAX_PAGE_PATH_LEN);
        String pageTitle = truncate(payload.getPageTitle(), MAX_PAGE_TITLE_LEN);
        String visitorId = truncate(defaultIfBlank(payload.getVisitorId(), "visitor-unknown"), MAX_VISITOR_LEN);
        String sessionId = truncate(defaultIfBlank(payload.getSessionId(), "session-unknown"), MAX_SESSION_LEN);
        long staySeconds = sanitizeStaySeconds(payload.getStaySeconds());
        String referrer = truncate(payload.getReferrer(), MAX_REFERRER_LEN);
        String lang = truncate(payload.getLang(), MAX_LANG_LEN);
        String timezone = truncate(payload.getTimezone(), MAX_TIMEZONE_LEN);
        String screenSize = buildScreenSize(payload.getScreenWidth(), payload.getScreenHeight());
        String ipHash = truncate(hashValue(resolveClientIp(httpServletRequest)), MAX_IP_HASH_LEN);
        String visitorHash = truncate(hashValue(visitorId), MAX_IP_HASH_LEN);
        String userAgent = truncate(httpServletRequest == null ? null : httpServletRequest.getHeader("User-Agent"), MAX_UA_LEN);

        String monthTable = monthTableName(eventDate);
        ensureMonthTable(monthTable);

        jdbcTemplate.update(
                "INSERT INTO " + monthTable + " (id, event_type, event_date, event_time, page_path, page_title, visitor_id, session_id, "
                        + "stay_seconds, referrer, lang, screen_size, timezone, ip_hash, user_agent, create_time) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())",
                nextId(), eventType, eventDate, eventTime, pagePath, pageTitle, visitorId, sessionId,
                "PAGE_LEAVE".equals(eventType) ? staySeconds : null, referrer, lang, screenSize, timezone, ipHash, userAgent
        );

        accumulateDaily(pagePath, pageTitle, eventDate, visitorHash, "PAGE_VIEW".equals(eventType), staySeconds);
        accumulateDaily(PAGE_ALL, "全部页面", eventDate, visitorHash, "PAGE_VIEW".equals(eventType), staySeconds);
    }

    private void accumulateDaily(String pagePath, String pageTitle, LocalDate statDate, String visitorHash, boolean isPageView, long staySeconds) {
        if (isPageView) {
            jdbcTemplate.update(
                    "INSERT INTO analytics_page_daily_stat (stat_date, page_path, page_title, pv_count, uv_count, leave_count, total_stay_seconds, update_time) "
                            + "VALUES (?, ?, ?, 1, 0, 0, 0, NOW()) "
                            + "ON DUPLICATE KEY UPDATE pv_count = pv_count + 1, page_title = IF(? <> '', ?, page_title), update_time = NOW()",
                    statDate, pagePath, pageTitle, defaultIfBlank(pageTitle, ""), defaultIfBlank(pageTitle, "")
            );

            int inserted = jdbcTemplate.update(
                    "INSERT IGNORE INTO analytics_page_daily_visitor (stat_date, page_path, visitor_hash, create_time) VALUES (?, ?, ?, NOW())",
                    statDate, pagePath, visitorHash
            );
            if (inserted > 0) {
                jdbcTemplate.update(
                        "UPDATE analytics_page_daily_stat SET uv_count = uv_count + 1, update_time = NOW() WHERE stat_date = ? AND page_path = ?",
                        statDate, pagePath
                );
            }
            return;
        }

        jdbcTemplate.update(
                "INSERT INTO analytics_page_daily_stat (stat_date, page_path, page_title, pv_count, uv_count, leave_count, total_stay_seconds, update_time) "
                        + "VALUES (?, ?, ?, 0, 0, 1, ?, NOW()) "
                        + "ON DUPLICATE KEY UPDATE leave_count = leave_count + 1, total_stay_seconds = total_stay_seconds + VALUES(total_stay_seconds), "
                        + "page_title = IF(? <> '', ?, page_title), update_time = NOW()",
                statDate, pagePath, pageTitle, staySeconds, defaultIfBlank(pageTitle, ""), defaultIfBlank(pageTitle, "")
        );
    }

    private List<AnalyticsDailyTrendVO> buildTrend(LocalDate startDate, LocalDate endDate) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT stat_date, pv_count, uv_count, leave_count, total_stay_seconds "
                        + "FROM analytics_page_daily_stat WHERE page_path = ? AND stat_date BETWEEN ? AND ? ORDER BY stat_date ASC",
                PAGE_ALL, startDate, endDate
        );
        Map<String, Map<String, Object>> map = new HashMap<String, Map<String, Object>>();
        for (Map<String, Object> row : rows) {
            Object day = row.get("stat_date");
            if (day == null) {
                continue;
            }
            map.put(String.valueOf(day), row);
        }

        List<AnalyticsDailyTrendVO> trend = new ArrayList<AnalyticsDailyTrendVO>();
        LocalDate cursor = startDate;
        while (!cursor.isAfter(endDate)) {
            String key = cursor.toString();
            Map<String, Object> row = map.get(key);
            long pv = row == null ? 0L : toLong(row.get("pv_count"));
            long uv = row == null ? 0L : toLong(row.get("uv_count"));
            long leaveCount = row == null ? 0L : toLong(row.get("leave_count"));
            long totalStay = row == null ? 0L : toLong(row.get("total_stay_seconds"));

            AnalyticsDailyTrendVO item = new AnalyticsDailyTrendVO();
            item.setStatDate(key);
            item.setPv(pv);
            item.setUv(uv);
            item.setAvgStaySeconds(leaveCount <= 0 ? 0L : Math.round((double) totalStay / (double) leaveCount));
            trend.add(item);
            cursor = cursor.plusDays(1);
        }
        return trend;
    }

    private List<AnalyticsTopPageVO> fetchTopPages(LocalDate startDate, LocalDate endDate, int topN) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT page_path, MAX(page_title) AS page_title, SUM(pv_count) AS pv, SUM(uv_count) AS uv, "
                        + "SUM(leave_count) AS leave_count, SUM(total_stay_seconds) AS stay_seconds "
                        + "FROM analytics_page_daily_stat "
                        + "WHERE page_path <> ? AND stat_date BETWEEN ? AND ? "
                        + "GROUP BY page_path ORDER BY pv DESC LIMIT ?",
                PAGE_ALL, startDate, endDate, topN
        );
        List<AnalyticsTopPageVO> list = new ArrayList<AnalyticsTopPageVO>();
        for (Map<String, Object> row : rows) {
            long leaveCount = toLong(row.get("leave_count"));
            long totalStay = toLong(row.get("stay_seconds"));

            AnalyticsTopPageVO item = new AnalyticsTopPageVO();
            item.setPagePath(String.valueOf(row.get("page_path")));
            Object pageTitleObj = row.get("page_title");
            item.setPageTitle(defaultIfBlank(pageTitleObj == null ? "" : String.valueOf(pageTitleObj), "-"));
            item.setPv(toLong(row.get("pv")));
            item.setUv(toLong(row.get("uv")));
            item.setAvgStaySeconds(leaveCount <= 0 ? 0L : Math.round((double) totalStay / (double) leaveCount));
            list.add(item);
        }
        return list;
    }

    private void ensureSummaryTables() {
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS analytics_page_daily_stat ("
                        + "stat_date DATE NOT NULL,"
                        + "page_path VARCHAR(255) NOT NULL,"
                        + "page_title VARCHAR(255) DEFAULT NULL,"
                        + "pv_count BIGINT NOT NULL DEFAULT 0,"
                        + "uv_count BIGINT NOT NULL DEFAULT 0,"
                        + "leave_count BIGINT NOT NULL DEFAULT 0,"
                        + "total_stay_seconds BIGINT NOT NULL DEFAULT 0,"
                        + "update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                        + "PRIMARY KEY (stat_date, page_path),"
                        + "KEY idx_page_path (page_path),"
                        + "KEY idx_update_time (update_time)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
        );
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS analytics_page_daily_visitor ("
                        + "stat_date DATE NOT NULL,"
                        + "page_path VARCHAR(255) NOT NULL,"
                        + "visitor_hash VARCHAR(64) NOT NULL,"
                        + "create_time DATETIME DEFAULT CURRENT_TIMESTAMP,"
                        + "PRIMARY KEY (stat_date, page_path, visitor_hash),"
                        + "KEY idx_page_visitor (page_path, visitor_hash)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
        );
        ensureIndex("analytics_page_daily_stat", "idx_page_date", "CREATE INDEX idx_page_date ON analytics_page_daily_stat (page_path, stat_date)");
        ensureIndex("analytics_page_daily_visitor", "idx_page_date_visitor", "CREATE INDEX idx_page_date_visitor ON analytics_page_daily_visitor (page_path, stat_date, visitor_hash)");
    }

    private void ensureMonthTable(String tableName) {
        if (!tableName.matches("^analytics_page_event_[0-9]{6}$")) {
            throw new IllegalArgumentException("invalid analytics table name: " + tableName);
        }
        if (ensuredMonthTables.contains(tableName)) {
            return;
        }
        synchronized (this) {
            if (ensuredMonthTables.contains(tableName)) {
                return;
            }
            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS " + tableName + " ("
                            + "id BIGINT NOT NULL,"
                            + "event_type VARCHAR(16) NOT NULL,"
                            + "event_date DATE NOT NULL,"
                            + "event_time DATETIME NOT NULL,"
                            + "page_path VARCHAR(255) NOT NULL,"
                            + "page_title VARCHAR(255) DEFAULT NULL,"
                            + "visitor_id VARCHAR(64) NOT NULL,"
                            + "session_id VARCHAR(64) DEFAULT NULL,"
                            + "stay_seconds BIGINT DEFAULT NULL,"
                            + "referrer VARCHAR(1024) DEFAULT NULL,"
                            + "lang VARCHAR(32) DEFAULT NULL,"
                            + "screen_size VARCHAR(32) DEFAULT NULL,"
                            + "timezone VARCHAR(64) DEFAULT NULL,"
                            + "ip_hash VARCHAR(64) DEFAULT NULL,"
                            + "user_agent VARCHAR(512) DEFAULT NULL,"
                            + "create_time DATETIME DEFAULT CURRENT_TIMESTAMP,"
                            + "PRIMARY KEY (id),"
                            + "KEY idx_event_date_path (event_date, page_path),"
                            + "KEY idx_event_type_time (event_type, event_time),"
                            + "KEY idx_visitor_time (visitor_id, event_time)"
                            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );
            ensuredMonthTables.add(tableName);
        }
    }

    private void ensureIndex(String tableName, String indexName, String ddl) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.STATISTICS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?",
                Integer.class,
                tableName,
                indexName
        );
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.execute(
                ddl
        );
    }

    private String monthTableName(LocalDate date) {
        YearMonth ym = YearMonth.of(date.getYear(), date.getMonth());
        return String.format(Locale.ROOT, "analytics_page_event_%04d%02d", ym.getYear(), ym.getMonthValue());
    }

    private LocalDateTime resolveEventTime(Long epochMillis) {
        if (epochMillis == null || epochMillis <= 0L) {
            return LocalDateTime.now();
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
    }

    private long sanitizeStaySeconds(Long staySeconds) {
        if (staySeconds == null || staySeconds < 0L) {
            return 0L;
        }
        return Math.min(staySeconds, 604800L);
    }

    private String buildScreenSize(Integer width, Integer height) {
        if (width == null || height == null || width <= 0 || height <= 0) {
            return null;
        }
        return width + "x" + height;
    }

    private int normalizeRangeDays(Integer days) {
        if (days == null) {
            return 7;
        }
        if (days < 1) {
            return 1;
        }
        return Math.min(days, 365);
    }

    private int normalizeTopN(Integer topN) {
        if (topN == null) {
            return 10;
        }
        if (topN < 1) {
            return 1;
        }
        return Math.min(topN, 50);
    }

    private String resolveClientIp(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String[] headerNames = new String[] {"CF-Connecting-IP", "X-Forwarded-For", "X-Real-IP"};
        for (int i = 0; i < headerNames.length; i += 1) {
            String value = request.getHeader(headerNames[i]);
            if (value == null || value.trim().isEmpty()) {
                continue;
            }
            if ("X-Forwarded-For".equalsIgnoreCase(headerNames[i])) {
                String[] arr = value.split(",");
                if (arr.length > 0) {
                    return arr[0].trim();
                }
            }
            return value.trim();
        }
        return defaultIfBlank(request.getRemoteAddr(), "");
    }

    private String hashValue(String value) {
        String source = defaultIfBlank(value, "") + "|" + defaultIfBlank(analyticsProperties.getIpSalt(), "");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (int i = 0; i < bytes.length; i += 1) {
                String hex = Integer.toHexString(bytes[i] & 0xff);
                if (hex.length() == 1) {
                    sb.append('0');
                }
                sb.append(hex);
            }
            return sb.toString();
        } catch (Exception ex) {
            return "";
        }
    }

    private long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ex) {
            return 0L;
        }
    }

    private String truncate(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        if (text.isEmpty()) {
            return null;
        }
        if (text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen);
    }

    private String defaultIfBlank(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = value.trim();
        return text.isEmpty() ? fallback : text;
    }

    private long nextId() {
        long now = System.currentTimeMillis();
        long random = (long) (Math.random() * 1000L);
        return now * 1000L + random;
    }
}
