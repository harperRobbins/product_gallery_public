package com.szwego.gallery.util;

import cn.hutool.json.JSONUtil;
import cn.hutool.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class TagUtil {
    private TagUtil() {
    }

    public static String toJson(List<String> tags) {
        if (tags == null) {
            return "[]";
        }
        return JSONUtil.toJsonStr(tags);
    }

    public static String toBilingualJson(List<String> zhTags, List<String> enTags) {
        JSONObject root = new JSONObject();
        root.set("zh", normalizeTags(zhTags));
        root.set("en", normalizeTags(enTags));
        return root.toString();
    }

    public static List<String> fromJson(String tagsJson) {
        if (tagsJson == null || tagsJson.trim().isEmpty()) {
            return new ArrayList<String>();
        }
        String raw = tagsJson.trim();
        if (raw.startsWith("{")) {
            JSONObject root = JSONUtil.parseObj(raw);
            List<String> zh = root.containsKey("zh") ? JSONUtil.toList(root.getJSONArray("zh"), String.class) : new ArrayList<String>();
            if (!zh.isEmpty()) {
                return normalizeTags(zh);
            }
            List<String> en = root.containsKey("en") ? JSONUtil.toList(root.getJSONArray("en"), String.class) : new ArrayList<String>();
            return normalizeTags(en);
        }
        return normalizeTags(JSONUtil.toList(raw, String.class));
    }

    public static List<String> fromJsonByLang(String tagsJson, String lang) {
        if (tagsJson == null || tagsJson.trim().isEmpty()) {
            return new ArrayList<String>();
        }
        String raw = tagsJson.trim();
        if (!raw.startsWith("{")) {
            return normalizeTags(JSONUtil.toList(raw, String.class));
        }
        JSONObject root = JSONUtil.parseObj(raw);
        boolean preferEn = lang != null && lang.toLowerCase(Locale.ROOT).startsWith("en");
        List<String> zh = root.containsKey("zh") ? normalizeTags(JSONUtil.toList(root.getJSONArray("zh"), String.class)) : new ArrayList<String>();
        List<String> en = root.containsKey("en") ? normalizeTags(JSONUtil.toList(root.getJSONArray("en"), String.class)) : new ArrayList<String>();
        if (preferEn) {
            return en.isEmpty() ? zh : en;
        }
        return zh.isEmpty() ? en : zh;
    }

    public static List<String> fromJsonAll(String tagsJson) {
        LinkedHashSet<String> merged = new LinkedHashSet<String>();
        List<String> zhOrDefault = fromJsonByLang(tagsJson, "zh");
        List<String> en = fromJsonByLang(tagsJson, "en");
        merged.addAll(zhOrDefault);
        merged.addAll(en);
        return new ArrayList<String>(merged);
    }

    private static List<String> normalizeTags(List<String> tags) {
        Set<String> dedup = new LinkedHashSet<String>();
        if (tags != null) {
            for (String tag : tags) {
                if (tag == null) {
                    continue;
                }
                String text = tag.trim();
                if (!text.isEmpty()) {
                    dedup.add(text);
                }
            }
        }
        return new ArrayList<String>(dedup);
    }
}
