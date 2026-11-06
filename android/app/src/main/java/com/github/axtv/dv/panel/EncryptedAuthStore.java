/*
 * 登录凭据的加密持久化实现（Android 侧）。
 * 对比原实现（SharedPreferences douyin_auth 中的明文），本实现使用
 * Android Keystore (AES/GCM) 加密敏感字段，通过 Base64 存于 Android SharedPreferences。
 */
package com.github.axtv.dv.panel;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;
import javax.crypto.KeyGenerator;
import java.util.Base64;

/**
 * AuthStore 的加密实现（Android 侧），使用 AES/GCM 通过 Android Keystore 保护敏感凭据。
 * 实现 {@link axtv.panel.AuthStore}。
 */
public final class EncryptedAuthStore implements axtv.panel.AuthStore {

    private static final String PREFS_NAME = "douyin_auth_enc";
    private static final String KEY_ALIAS = "ax_tv_auth_key";
    private static final String PREFIX = "ENC.";

    private final SharedPreferences sharedPreferences;
    private final String cookie;
    private final String payload;
    private final String ticket;
    private final String tsSign;
    private final String privateKey;
    private final String serverCert;

    private EncryptedAuthStore(SharedPreferences sharedPreferences) {
        this.sharedPreferences = sharedPreferences;
        this.cookie = decryptIfPossible(sharedPreferences.getString("cookie_string", ""), "cookie_string");
        this.payload = decryptIfPossible(sharedPreferences.getString("interaction_security_payload", ""), "interaction_security_payload");
        this.ticket = decryptIfPossible(sharedPreferences.getString("interaction_ticket", ""), "interaction_ticket");
        this.tsSign = decryptIfPossible(sharedPreferences.getString("interaction_ts_sign", ""), "interaction_ts_sign");
        this.privateKey = decryptIfPossible(sharedPreferences.getString("interaction_private_key", ""), "interaction_private_key");
        this.serverCert = decryptIfPossible(sharedPreferences.getString("interaction_server_cert", ""), "interaction_server_cert");
    }

    /** 创建加密 SharedPreferences 并返回 EncryptedAuthStore 实例。 */
    public static EncryptedAuthStore create(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return new EncryptedAuthStore(sharedPreferences);
    }

    // ----- axtv.panel.AuthStore 接口方法 -----

    @Override
    public String cookie() {
        return cookie;
    }

    @Override
    public void setCookie(String cookie) {
        if (cookie == null) cookie = "";
        try {
            Key key = getOrCreateKey();
            byte[] ivAndCipher = encrypt(cookie, key);
            sharedPreferences.edit().putString("cookie_string", PREFIX + Base64.getEncoder().encodeToString(ivAndCipher)).apply();
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("Failed to encrypt cookie", e);
        }
    }

    @Override
    public String interactionSecurityPayload() {
        return payload;
    }

    @Override
    public String interactionPrivateKey() {
        return privateKey;
    }

    @Override
    public axtv.panel.InteractionSecurityConfig interactionSecurity() {
        try {
            return new axtv.panel.InteractionSecurityConfig(payload, ticket, tsSign, privateKey, serverCert);
        } catch (Exception e) {
            return new axtv.panel.InteractionSecurityConfig("", "", "", "", "");
        }
    }

    @Override
    public void setInteractionSecurity(String rawPayload, axtv.panel.InteractionSecurityConfig config) {
        try {
            Key key = getOrCreateKey();
            sharedPreferences.edit()
                    .putString("interaction_security_payload", PREFIX + Base64.getEncoder().encodeToString(encrypt(rawPayload, key)))
                    .putString("interaction_ticket", PREFIX + Base64.getEncoder().encodeToString(encrypt(config.ticket(), key)))
                    .putString("interaction_ts_sign", PREFIX + Base64.getEncoder().encodeToString(encrypt(config.tsSign(), key)))
                    .putString("interaction_private_key", PREFIX + Base64.getEncoder().encodeToString(encrypt(config.privateKey(), key)))
                    .putString("interaction_server_cert", PREFIX + Base64.getEncoder().encodeToString(encrypt(config.serverCert(), key)))
                    .apply();
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("Failed to encrypt auth store fields", e);
        }
    }

    private static Key getOrCreateKeyStatic() throws GeneralSecurityException {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        try {
            keyStore.load(null);
        } catch (java.io.IOException e) {
            throw new java.security.KeyStoreException("AndroidKeyStore 初始化失败", e);
        }
        Key existing = keyStore.getKey(KEY_ALIAS, null);
        if (existing != null) {
            return existing;
        }
        KeyGenerator keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        keyGen.init(new KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return keyGen.generateKey();
    }

    private Key getOrCreateKey() throws GeneralSecurityException {
        return getOrCreateKeyStatic();
    }

    private static byte[] encrypt(String plaintext, Key key) throws GeneralSecurityException {
        byte[] iv = new byte[12];
        new java.security.SecureRandom().nextBytes(iv);
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key, new javax.crypto.spec.GCMParameterSpec(128, iv));
        byte[] plain = (plaintext == null ? "" : plaintext).getBytes(StandardCharsets.UTF_8);
        byte[] ciphertext = cipher.doFinal(plain);
        byte[] result = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, result, 0, iv.length);
        System.arraycopy(ciphertext, 0, result, iv.length, ciphertext.length);
        return result;
    }

    private static String decryptIfPossible(String stored, String contextKey) {
        if (stored == null || stored.isEmpty()) return "";
        if (stored.startsWith(PREFIX)) {
            try {
                byte[] data = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
                byte[] iv = new byte[12];
                byte[] ciphertext = new byte[data.length - 12];
                System.arraycopy(data, 0, iv, 0, 12);
                System.arraycopy(data, 12, ciphertext, 0, data.length - 12);
                Key key = getOrCreateKeyStatic();
                javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(javax.crypto.Cipher.DECRYPT_MODE, key, new javax.crypto.spec.GCMParameterSpec(128, iv));
                byte[] plaintext = cipher.doFinal(ciphertext);
                return new String(plaintext, StandardCharsets.UTF_8);
            } catch (GeneralSecurityException e) {
                return stored;
            }
        }
        return stored;
    }
}
