package com.szwego.gallery.util;

public final class LanguageUtil {

    public static final String DEFAULT_LANG = "zh-CN";
    public static final String EN_LANG = "en-US";

    private LanguageUtil() {
    }

    public static String normalize(String lang) {
        if (lang == null || lang.trim().isEmpty()) {
            return DEFAULT_LANG;
        }
        String raw = lang.trim().replace('_', '-');
        String lower = raw.toLowerCase();
        if (lower.startsWith("zh")) {
            return DEFAULT_LANG;
        }
        if (lower.startsWith("en")) {
            return EN_LANG;
        }
        return raw;
    }

    public static boolean isDefaultLang(String lang) {
        return DEFAULT_LANG.equalsIgnoreCase(normalize(lang));
    }
}

