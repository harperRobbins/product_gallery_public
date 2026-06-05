package com.szwego.gallery.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.szwego.gallery.common.BusinessException;
import com.szwego.gallery.config.LlmProperties;
import com.szwego.gallery.domain.LlmConfig;
import com.szwego.gallery.dto.LlmConfigSaveRequest;
import com.szwego.gallery.dto.LlmConfigTestRequest;
import com.szwego.gallery.dto.LlmConfigVO;
import com.szwego.gallery.dto.LlmTranslationResult;
import com.szwego.gallery.mapper.LlmConfigMapper;
import com.szwego.gallery.service.LlmService;
import com.szwego.gallery.util.WsAlbumTokenCryptoUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LlmServiceImpl implements LlmService {

    private final LlmConfigMapper llmConfigMapper;
    private final LlmProperties llmProperties;
    private final ObjectMapper objectMapper;

    private static final String DEFAULT_PROVIDER = "openai-compatible";

    @Override
    public LlmConfigVO getConfig() {
        LlmConfig row = getLatestConfig();
        if (row == null) {
            return buildDefaultVO();
        }
        return toVO(row, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LlmConfigVO saveConfig(LlmConfigSaveRequest request) {
        LlmConfig row = request.getId() == null ? getLatestConfig() : llmConfigMapper.selectById(request.getId());
        if (row == null) {
            row = new LlmConfig();
        }

        row.setEnabled(request.getEnabled() == null ? 0 : (request.getEnabled() == 1 ? 1 : 0));
        row.setProvider(StrUtil.blankToDefault(request.getProvider(), DEFAULT_PROVIDER));
        row.setBaseUrl(normalizeBaseUrl(request.getBaseUrl()));
        row.setModel(StrUtil.trimToNull(request.getModel()));
        row.setTemperature(normalizeTemperature(request.getTemperature()));
        row.setMaxTokens(normalizeMaxTokens(request.getMaxTokens()));
        row.setTargetLang(StrUtil.blankToDefault(request.getTargetLang(), "en"));
        row.setStrictMode(request.getStrictMode() != null && request.getStrictMode() == 1 ? 1 : 0);
        row.setSystemPrompt(StrUtil.trimToNull(request.getSystemPrompt()));
        row.setUserPromptTemplate(StrUtil.trimToNull(request.getUserPromptTemplate()));

        if (StrUtil.isNotBlank(request.getApiKey())) {
            row.setApiKeyEnc(encryptApiKey(request.getApiKey().trim()));
        } else if (row.getId() == null) {
            row.setApiKeyEnc(null);
        }

        validateConfig(row, true);

        if (row.getId() == null) {
            llmConfigMapper.insert(row);
        } else {
            llmConfigMapper.updateById(row);
        }
        return toVO(llmConfigMapper.selectById(row.getId()), true);
    }

    @Override
    public LlmTranslationResult testConfig(LlmConfigTestRequest request) {
        try {
            RuntimeConfig runtime = buildRuntimeConfigForTest(request);
            String title = StrUtil.blankToDefault(StrUtil.trimToNull(request.getTitle()), "新款格纹公文包，支持商务和日常通勤。");
            String description = StrUtil.blankToDefault(StrUtil.trimToNull(request.getDescription()), "容量大，质感好，支持肩背和手提。");
            LlmTranslationResult result = callModel(runtime, title, description);
            String resultTitle = StrUtil.blankToDefault(result.getEnTitle(), "(empty)");
            persistTestStatus(true, "OK: " + StrUtil.sub(resultTitle, 0, 120));
            return result;
        } catch (Exception ex) {
            persistTestStatus(false, normalizeError(ex));
            throw ex;
        }
    }

    @Override
    public LlmTranslationResult summarizeAndTranslateToEnglish(String title, String description) {
        RuntimeConfig runtime = buildRuntimeConfigFromDb();
        if (runtime == null) {
            return null;
        }
        try {
            return callModel(runtime, title, description);
        } catch (Exception ex) {
            if (runtime.strictMode) {
                throw new BusinessException("大模型总结翻译失败: " + normalizeError(ex));
            }
            return null;
        }
    }

    @Override
    public LlmTranslationResult summarizeAndTranslateBilingual(String title, String description) {
        RuntimeConfig runtime = buildRuntimeConfigFromDb();
        if (runtime == null) {
            return null;
        }
        try {
            return callModelBilingual(runtime, title, description);
        } catch (Exception ex) {
            if (runtime.strictMode) {
                throw new BusinessException("大模型双语总结失败: " + normalizeError(ex));
            }
            return null;
        }
    }

    private RuntimeConfig buildRuntimeConfigForTest(LlmConfigTestRequest request) {
        LlmConfig saved = getLatestConfig();
        RuntimeConfig runtime = new RuntimeConfig();
        runtime.provider = StrUtil.blankToDefault(request.getProvider(), saved == null ? DEFAULT_PROVIDER : saved.getProvider());
        runtime.baseUrl = normalizeBaseUrl(StrUtil.blankToDefault(request.getBaseUrl(), saved == null ? "" : saved.getBaseUrl()));
        runtime.apiKey = StrUtil.trimToNull(request.getApiKey());
        if (StrUtil.isBlank(runtime.apiKey) && saved != null) {
            runtime.apiKey = decryptApiKey(saved.getApiKeyEnc());
        }
        runtime.model = StrUtil.trimToNull(request.getModel());
        if (StrUtil.isBlank(runtime.model) && saved != null) {
            runtime.model = saved.getModel();
        }
        runtime.temperature = normalizeTemperature(request.getTemperature() != null ? request.getTemperature() : (saved == null ? null : saved.getTemperature()));
        runtime.maxTokens = normalizeMaxTokens(request.getMaxTokens() != null ? request.getMaxTokens() : (saved == null ? null : saved.getMaxTokens()));
        runtime.targetLang = StrUtil.blankToDefault(request.getTargetLang(), saved == null ? "en" : saved.getTargetLang());
        runtime.systemPrompt = StrUtil.trimToNull(StrUtil.blankToDefault(request.getSystemPrompt(), saved == null ? null : saved.getSystemPrompt()));
        runtime.userPromptTemplate = StrUtil.trimToNull(StrUtil.blankToDefault(request.getUserPromptTemplate(), saved == null ? null : saved.getUserPromptTemplate()));
        runtime.strictMode = true;
        validateRuntime(runtime);
        return runtime;
    }

    private RuntimeConfig buildRuntimeConfigFromDb() {
        LlmConfig row = getLatestConfig();
        if (row == null || row.getEnabled() == null || row.getEnabled() != 1) {
            return null;
        }
        RuntimeConfig runtime = new RuntimeConfig();
        runtime.provider = StrUtil.blankToDefault(row.getProvider(), DEFAULT_PROVIDER);
        runtime.baseUrl = normalizeBaseUrl(row.getBaseUrl());
        runtime.apiKey = decryptApiKey(row.getApiKeyEnc());
        runtime.model = StrUtil.trimToNull(row.getModel());
        runtime.temperature = normalizeTemperature(row.getTemperature());
        runtime.maxTokens = normalizeMaxTokens(row.getMaxTokens());
        runtime.targetLang = StrUtil.blankToDefault(row.getTargetLang(), "en");
        runtime.systemPrompt = StrUtil.trimToNull(row.getSystemPrompt());
        runtime.userPromptTemplate = StrUtil.trimToNull(row.getUserPromptTemplate());
        runtime.strictMode = row.getStrictMode() != null && row.getStrictMode() == 1;
        validateRuntime(runtime);
        return runtime;
    }

    private LlmTranslationResult callModel(RuntimeConfig runtime, String title, String description) {
        boolean preferTitleOnly = shouldPreferTitleOnly(title, description);
        Map<String, Object> payload = buildChatPayload(runtime, buildUserPrompt(runtime, title, description));

        String responseText;
        try {
            String apiUrl = runtime.baseUrl + "/chat/completions";
            responseText = executeWithTokenCompatibility(apiUrl, runtime, payload);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("LLM请求失败: " + normalizeError(ex));
        }

        String content = parseMessageContent(responseText);
        LlmTranslationResult result = parseTranslationResult(content);
        if (preferTitleOnly) {
            result.setEnDescription("");
        }
        if (StrUtil.isBlank(result.getEnTitle()) && StrUtil.isBlank(result.getEnDescription())) {
            throw new BusinessException("LLM返回为空");
        }
        return result;
    }

    private LlmTranslationResult callModelBilingual(RuntimeConfig runtime, String title, String description) {
        boolean preferTitleOnly = shouldPreferTitleOnly(title, description);
        Map<String, Object> payload = buildChatPayload(runtime, buildBilingualPrompt(title, description));
        String responseText;
        try {
            String apiUrl = runtime.baseUrl + "/chat/completions";
            responseText = executeWithTokenCompatibility(apiUrl, runtime, payload);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("LLM请求失败: " + normalizeError(ex));
        }
        String content = parseMessageContent(responseText);
        LlmTranslationResult result = parseTranslationResult(content);
        if (preferTitleOnly) {
            result.setZhDescription("");
            result.setEnDescription("");
        }
        if (StrUtil.isBlank(result.getZhTitle()) && StrUtil.isBlank(result.getEnTitle())) {
            throw new BusinessException("LLM返回为空");
        }
        return result;
    }

    private Map<String, Object> buildChatPayload(RuntimeConfig runtime, String userContent) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("model", runtime.model);
        List<Map<String, String>> messages = new ArrayList<Map<String, String>>();
        Map<String, String> systemMsg = new LinkedHashMap<String, String>();
        systemMsg.put("role", "system");
        systemMsg.put("content", StrUtil.blankToDefault(runtime.systemPrompt, defaultSystemPrompt()));
        messages.add(systemMsg);
        Map<String, String> userMsg = new LinkedHashMap<String, String>();
        userMsg.put("role", "user");
        userMsg.put("content", userContent);
        messages.add(userMsg);
        payload.put("messages", messages);
        return payload;
    }

    private String executeWithTokenCompatibility(String apiUrl, RuntimeConfig runtime, Map<String, Object> basePayload) throws Exception {
        String lastErr = "";
        int lastStatus = 0;
        boolean[] temperatureOptions = new boolean[]{true, false};
        boolean[] tokenOptions = new boolean[]{true, false}; // true=max_completion_tokens, false=max_tokens
        for (boolean withTemperature : temperatureOptions) {
            for (boolean useCompletionTokens : tokenOptions) {
                Map<String, Object> payload = new LinkedHashMap<String, Object>(basePayload);
                if (withTemperature) {
                    payload.put("temperature", runtime.temperature);
                }
                if (useCompletionTokens) {
                    payload.put("max_completion_tokens", runtime.maxTokens);
                } else {
                    payload.put("max_tokens", runtime.maxTokens);
                }
                HttpResponse response = executeChat(apiUrl, runtime.apiKey, payload);
                String body = response.body();
                if (response.isOk()) {
                    return body;
                }
                lastStatus = response.getStatus();
                lastErr = StrUtil.sub(body, 0, 500);
                if (!isCompatRetryableError(lastErr)) {
                    throw new BusinessException("LLM请求失败 HTTP " + lastStatus + ": " + StrUtil.sub(body, 0, 400));
                }
            }
        }
        throw new BusinessException("LLM请求失败 HTTP " + lastStatus + ": " + StrUtil.sub(lastErr, 0, 400));
    }

    private boolean isCompatRetryableError(String err) {
        if (StrUtil.isBlank(err)) {
            return false;
        }
        return err.contains("max_completion_tokens")
                || err.contains("max_tokens")
                || err.contains("temperature");
    }

    private HttpResponse executeChat(String apiUrl, String apiKey, Map<String, Object> payload) throws Exception {
        String body = objectMapper.writeValueAsString(payload);
        return HttpRequest.post(apiUrl)
                .timeout(resolveTimeout())
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(body)
                .execute();
    }

    private int resolveTimeout() {
        Integer timeout = llmProperties.getRequestTimeoutMs();
        if (timeout == null || timeout < 5000) {
            return 60000;
        }
        return timeout;
    }

    private String parseMessageContent(String responseText) {
        try {
            JsonNode root = objectMapper.readTree(responseText);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.size() == 0) {
                throw new BusinessException("LLM返回缺少choices");
            }
            String content = choices.get(0).path("message").path("content").asText("");
            if (StrUtil.isBlank(content)) {
                throw new BusinessException("LLM返回内容为空");
            }
            return content;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("解析LLM返回失败: " + normalizeError(ex));
        }
    }

    private LlmTranslationResult parseTranslationResult(String content) {
        String jsonText = extractJson(content);
        if (StrUtil.isBlank(jsonText)) {
            throw new BusinessException("LLM返回非JSON格式");
        }
        try {
            JsonNode node = objectMapper.readTree(jsonText);
            LlmTranslationResult result = new LlmTranslationResult();
            result.setZhTitle(readFirst(node, "zhTitle", "cnTitle", "titleZh", "title_zh", "title_cn"));
            result.setZhDescription(readFirst(node, "zhDescription", "cnDescription", "descriptionZh", "description_zh", "description_cn"));
            result.setEnTitle(readFirst(node, "enTitle", "translatedTitle", "title"));
            result.setEnDescription(readFirst(node, "enDescription", "translatedDescription", "description"));
            return result;
        } catch (Exception ex) {
            throw new BusinessException("解析翻译JSON失败: " + normalizeError(ex));
        }
    }

    private String readFirst(JsonNode node, String... keys) {
        for (String key : keys) {
            String value = StrUtil.trimToEmpty(node.path(key).asText(""));
            if (StrUtil.isNotBlank(value)) {
                return value;
            }
        }
        return "";
    }

    private String extractJson(String text) {
        if (StrUtil.isBlank(text)) {
            return "";
        }
        String raw = text.trim();
        if (raw.startsWith("```")) {
            int firstBrace = raw.indexOf('{');
            int lastBrace = raw.lastIndexOf('}');
            if (firstBrace >= 0 && lastBrace > firstBrace) {
                return raw.substring(firstBrace, lastBrace + 1);
            }
        }
        int firstBrace = raw.indexOf('{');
        int lastBrace = raw.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return raw.substring(firstBrace, lastBrace + 1);
        }
        return "";
    }

    private String buildUserPrompt(RuntimeConfig runtime, String title, String description) {
        String template = StrUtil.blankToDefault(runtime.userPromptTemplate, defaultUserPromptTemplate());
        String safeTitle = StrUtil.blankToDefault(title, "");
        String safeDesc = StrUtil.blankToDefault(description, "");
        return template
                .replace("{{title}}", safeTitle)
                .replace("{{description}}", safeDesc)
                .replace("{{targetLang}}", runtime.targetLang)
                + "\n附加硬约束："
                + "\n- 若标题是简短图文（如：大货细节图、五金细节、细节图、实拍图），可只返回简短 enTitle，enDescription 置空。"
                + "\n- 若标题本身信息丰富（即使原始描述为空），可生成一句简短 enDescription。"
                + "\n- 输出尽量短，不要扩写不存在的信息。";
    }

    private String buildBilingualPrompt(String title, String description) {
        String safeTitle = StrUtil.blankToDefault(title, "");
        String safeDesc = StrUtil.blankToDefault(description, "");
        return "请基于给定中文文案，提炼商品要点，并同时输出中文与英文版本。"
                + "\n要求："
                + "\n1) 只输出JSON，不要任何额外文本"
                + "\n2) JSON字段固定为 zhTitle, zhDescription, enTitle, enDescription"
                + "\n3) 标题要简洁，保留品牌/系列/材质/尺寸等关键要素，不要照抄整段原文"
                + "\n4) 必须去掉价格相关文案、口令码、8位数字价码、货币符号和价格词（如 ¥、￥、RMB、CNY、价格、售价、🔍）"
                + "\n5) 若文案很短或仅为短标题，zhDescription 和 enDescription 可以为空字符串"
                + "\n6) enTitle 不超过 18 个英文单词，enDescription 最多 40 个英文单词"
                + "\n7) 英文字段必须使用英文品牌标准名；例如 古奇/GU..CCI/GUCCI -> Gucci，LV/路易威登 -> Louis Vuitton，爱马仕/Hermes/Hermè -> Hermès，菲拉格慕 -> Salvatore Ferragamo"
                + "\n8) 英文字段不要保留中文品牌词或中英文混写标签"
                + "\n标题: " + safeTitle
                + "\n描述: " + safeDesc;
    }

    private String defaultSystemPrompt() {
        return "You are a professional ecommerce copywriter and translator. Keep output concise and commercially readable.";
    }

    private String defaultUserPromptTemplate() {
        return "请先提炼商品信息要点，再翻译为{{targetLang}}。"
                + "\n要求："
                + "\n1) 只输出JSON，不要任何额外文本"
                + "\n2) JSON字段固定为 enTitle, enDescription（允许 enDescription 为空字符串）"
                + "\n3) enTitle 不超过 18 个英文单词，保留型号/货号等关键信息"
                + "\n4) enDescription 为可选；若标题与描述都很短可置空；若标题本身信息充分，可生成 1 句简短描述（最多 40 个英文单词）"
                + "\n标题: {{title}}"
                + "\n描述: {{description}}";
    }

    private boolean shouldPreferTitleOnly(String title, String description) {
        String t = StrUtil.trimToEmpty(title);
        String d = StrUtil.trimToEmpty(description);
        if (StrUtil.isBlank(t)) {
            return false;
        }
        if (isShortCaption(t) && (StrUtil.isBlank(d) || d.length() <= 24)) {
            return true;
        }
        String lower = t.toLowerCase(Locale.ROOT);
        boolean detailLike = t.contains("细节")
                || t.contains("五金")
                || t.contains("大货")
                || t.contains("实拍")
                || t.contains("上身")
                || t.contains("图")
                || lower.contains("detail")
                || lower.contains("hardware");
        return detailLike && t.length() <= 18 && d.length() <= 40;
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

    private void validateConfig(LlmConfig config, boolean validateIfEnabled) {
        if (!validateIfEnabled || (config.getEnabled() != null && config.getEnabled() == 1)) {
            RuntimeConfig runtime = new RuntimeConfig();
            runtime.provider = StrUtil.blankToDefault(config.getProvider(), DEFAULT_PROVIDER);
            runtime.baseUrl = normalizeBaseUrl(config.getBaseUrl());
            runtime.apiKey = decryptApiKey(config.getApiKeyEnc());
            runtime.model = StrUtil.trimToNull(config.getModel());
            runtime.temperature = normalizeTemperature(config.getTemperature());
            runtime.maxTokens = normalizeMaxTokens(config.getMaxTokens());
            runtime.targetLang = StrUtil.blankToDefault(config.getTargetLang(), "en");
            runtime.systemPrompt = StrUtil.trimToNull(config.getSystemPrompt());
            runtime.userPromptTemplate = StrUtil.trimToNull(config.getUserPromptTemplate());
            runtime.strictMode = config.getStrictMode() != null && config.getStrictMode() == 1;
            validateRuntime(runtime);
        }
    }

    private void validateRuntime(RuntimeConfig runtime) {
        if (StrUtil.isBlank(runtime.baseUrl)) {
            throw new BusinessException("LLM Base URL不能为空");
        }
        if (StrUtil.isBlank(runtime.apiKey)) {
            throw new BusinessException("LLM API Key不能为空");
        }
        if (StrUtil.isBlank(runtime.model)) {
            throw new BusinessException("LLM Model不能为空");
        }
    }

    private LlmConfig getLatestConfig() {
        return llmConfigMapper.selectOne(new LambdaQueryWrapper<LlmConfig>()
                .orderByDesc(LlmConfig::getUpdateTime)
                .orderByDesc(LlmConfig::getId)
                .last("limit 1"));
    }

    private LlmConfigVO toVO(LlmConfig row, boolean maskApiKey) {
        LlmConfigVO vo = new LlmConfigVO();
        BeanUtils.copyProperties(row, vo);
        String apiKey = decryptApiKey(row.getApiKeyEnc());
        vo.setApiKeyMasked(maskApiKey ? WsAlbumTokenCryptoUtil.maskToken(apiKey) : apiKey);
        if (vo.getEnabled() == null) {
            vo.setEnabled(0);
        }
        if (StrUtil.isBlank(vo.getProvider())) {
            vo.setProvider(DEFAULT_PROVIDER);
        }
        if (StrUtil.isBlank(vo.getBaseUrl())) {
            vo.setBaseUrl("https://api.openai.com/v1");
        }
        if (StrUtil.isBlank(vo.getModel())) {
            vo.setModel("gpt-4o-mini");
        }
        if (vo.getTemperature() == null) {
            vo.setTemperature(new BigDecimal("0.30"));
        }
        if (vo.getMaxTokens() == null) {
            vo.setMaxTokens(800);
        }
        if (StrUtil.isBlank(vo.getTargetLang())) {
            vo.setTargetLang("en");
        }
        if (vo.getStrictMode() == null) {
            vo.setStrictMode(0);
        }
        if (StrUtil.isBlank(vo.getSystemPrompt())) {
            vo.setSystemPrompt(defaultSystemPrompt());
        }
        if (StrUtil.isBlank(vo.getUserPromptTemplate())) {
            vo.setUserPromptTemplate(defaultUserPromptTemplate());
        }
        return vo;
    }

    private LlmConfigVO buildDefaultVO() {
        LlmConfigVO vo = new LlmConfigVO();
        vo.setId(null);
        vo.setEnabled(0);
        vo.setProvider(DEFAULT_PROVIDER);
        vo.setBaseUrl("https://api.openai.com/v1");
        vo.setApiKeyMasked("");
        vo.setModel("gpt-4o-mini");
        vo.setTemperature(new BigDecimal("0.30"));
        vo.setMaxTokens(800);
        vo.setTargetLang("en");
        vo.setStrictMode(0);
        vo.setSystemPrompt(defaultSystemPrompt());
        vo.setUserPromptTemplate(defaultUserPromptTemplate());
        return vo;
    }

    private String normalizeBaseUrl(String baseUrl) {
        String value = StrUtil.trimToEmpty(baseUrl);
        if (value.isEmpty()) {
            return "";
        }
        if (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.endsWith("/v1")) {
            return value;
        }
        return value + "/v1";
    }

    private BigDecimal normalizeTemperature(BigDecimal temperature) {
        if (temperature == null) {
            return new BigDecimal("0.30");
        }
        if (temperature.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        if (temperature.compareTo(new BigDecimal("2.00")) > 0) {
            return new BigDecimal("2.00");
        }
        return temperature;
    }

    private Integer normalizeMaxTokens(Integer maxTokens) {
        if (maxTokens == null || maxTokens < 64) {
            return 800;
        }
        return Math.min(maxTokens, 4096);
    }

    private String encryptApiKey(String apiKey) {
        return WsAlbumTokenCryptoUtil.encrypt(apiKey, llmProperties.getKeySecret());
    }

    private String decryptApiKey(String cipher) {
        if (StrUtil.isBlank(cipher)) {
            return "";
        }
        return WsAlbumTokenCryptoUtil.decrypt(cipher, llmProperties.getKeySecret());
    }

    private String normalizeError(Exception ex) {
        if (ex == null) {
            return "unknown";
        }
        if (StrUtil.isNotBlank(ex.getMessage())) {
            return ex.getMessage();
        }
        if (ex.getCause() != null && StrUtil.isNotBlank(ex.getCause().getMessage())) {
            return ex.getCause().getMessage();
        }
        return ex.getClass().getSimpleName().toLowerCase(Locale.ROOT);
    }

    private void persistTestStatus(boolean success, String message) {
        LlmConfig row = getLatestConfig();
        if (row == null) {
            return;
        }
        row.setLastTestTime(LocalDateTime.now());
        row.setLastTestStatus(success ? 1 : 0);
        row.setLastTestMessage(StrUtil.sub(StrUtil.blankToDefault(message, ""), 0, 500));
        llmConfigMapper.updateById(row);
    }

    private static class RuntimeConfig {
        private String provider;
        private String baseUrl;
        private String apiKey;
        private String model;
        private BigDecimal temperature;
        private Integer maxTokens;
        private String targetLang;
        private String systemPrompt;
        private String userPromptTemplate;
        private boolean strictMode;
    }
}
