package com.silvertown.global.security.crypto;

import com.silvertown.global.common.exception.BusinessException;
import com.silvertown.global.common.exception.ErrorCode;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AccountNumberCrypto {
    private static final byte FORMAT_VERSION = 1;
    private static final int KEY_LENGTH_BYTES = 32;
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final SecretKeySpec key;

    public AccountNumberCrypto(@Value("${account.crypto-key:${ACCOUNT_CRYPTO_KEY:}}") String encodedKey) {
        if (encodedKey == null || encodedKey.isBlank()) {
            this.key = null;
            return;
        }
        try {
            byte[] keyBytes = Base64.getDecoder().decode(encodedKey);
            if (keyBytes.length != KEY_LENGTH_BYTES) {
                throw new IllegalStateException("ACCOUNT_CRYPTO_KEY must decode to 32 bytes");
            }
            this.key = new SecretKeySpec(keyBytes, "AES");
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("ACCOUNT_CRYPTO_KEY must be Base64 encoded", exception);
        }
    }

    public byte[] encrypt(String accountNumber) {
        ensureConfigured();
        byte[] iv = new byte[IV_LENGTH_BYTES];
        SECURE_RANDOM.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(accountNumber.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(1 + iv.length + ciphertext.length)
                    .put(FORMAT_VERSION).put(iv).put(ciphertext).array();
        } catch (GeneralSecurityException exception) {
            throw cryptoFailure(exception);
        }
    }

    public String decrypt(byte[] payload) {
        ensureConfigured();
        if (payload == null || payload.length <= 1 + IV_LENGTH_BYTES || payload[0] != FORMAT_VERSION) {
            throw cryptoFailure(null);
        }
        byte[] iv = new byte[IV_LENGTH_BYTES];
        byte[] ciphertext = new byte[payload.length - 1 - IV_LENGTH_BYTES];
        System.arraycopy(payload, 1, iv, 0, iv.length);
        System.arraycopy(payload, 1 + iv.length, ciphertext, 0, ciphertext.length);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw cryptoFailure(exception);
        }
    }

    private void ensureConfigured() {
        if (key == null) {
            throw new BusinessException(ErrorCode.ACCOUNT_CRYPTO_NOT_CONFIGURED);
        }
    }

    private BusinessException cryptoFailure(Exception cause) {
        BusinessException exception = new BusinessException(ErrorCode.ACCOUNT_CRYPTO_FAILURE);
        if (cause != null) exception.initCause(cause);
        return exception;
    }
}
