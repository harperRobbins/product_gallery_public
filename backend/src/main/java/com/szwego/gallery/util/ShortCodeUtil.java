package com.szwego.gallery.util;

import cn.hutool.core.util.RandomUtil;

public class ShortCodeUtil {
    private static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";

    private ShortCodeUtil() {
    }

    public static String randomCode(int length) {
        return RandomUtil.randomString(ALPHABET, length);
    }
}
