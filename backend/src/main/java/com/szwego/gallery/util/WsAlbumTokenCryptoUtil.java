package com.szwego.gallery.util;

import cn.hutool.core.util.StrUtil;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

public class WsAlbumTokenCryptoUtil {
    private WsAlbumTokenCryptoUtil() {
    }

    public static String encrypt(String plainText, String secret) {
        if (StrUtil.isBlank(plainText)) {
            return "";
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(buildKey(secret), "AES"));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new IllegalStateException("token加密失败: " + e.getMessage(), e);
        }
    }

    public static String decrypt(String cipherText, String secret) {
        if (StrUtil.isBlank(cipherText)) {
            return "";
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(buildKey(secret), "AES"));
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(cipherText));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("token解密失败: " + e.getMessage(), e);
        }
    }

    private static byte[] buildKey(String secret) throws Exception {
        String value = StrUtil.blankToDefault(secret, "change_this_ws_album_secret");
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        byte[] digest = md5.digest(value.getBytes(StandardCharsets.UTF_8));
        byte[] key = new byte[16];
        System.arraycopy(digest, 0, key, 0, 16);
        return key;
    }

    public static String maskToken(String token) {
        if (StrUtil.isBlank(token)) {
            return "";
        }
        String trimmed = token.trim();
        if (trimmed.length() <= 10) {
            return "******";
        }
        return trimmed.substring(0, 4) + "******" + trimmed.substring(trimmed.length() - 4);
    }
}
