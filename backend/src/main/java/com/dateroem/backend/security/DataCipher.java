package com.dateroem.backend.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Encrypts worker ID numbers and document photos at rest (migration 040):
 * AES-256-GCM with app.workers.data-secret, a fresh 12-byte nonce each time,
 * stored as nonce + ciphertext. Without the key the worker features answer
 * 503 rather than storing anything readable.
 */
@Component
public class DataCipher {

    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public DataCipher(@Value("${app.workers.data-secret:}") String secret) {
        SecretKeySpec parsed = null;
        if (secret != null && !secret.isBlank()) {
            byte[] bytes = Base64.getDecoder().decode(secret.trim());
            if (bytes.length == 32) {
                parsed = new SecretKeySpec(bytes, "AES");
            }
        }
        this.key = parsed;
    }

    public boolean isConfigured() {
        return key != null;
    }

    public byte[] encrypt(byte[] plain) {
        requireKey();
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] sealed = cipher.doFinal(plain);
            return ByteBuffer.allocate(NONCE_BYTES + sealed.length).put(nonce).put(sealed).array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not encrypt", e);
        }
    }

    public byte[] decrypt(byte[] stored) {
        requireKey();
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, stored, 0, NONCE_BYTES));
            return cipher.doFinal(stored, NONCE_BYTES, stored.length - NONCE_BYTES);
        } catch (GeneralSecurityException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "This was saved with another key and can't be read - is app.workers.data-secret the one it was saved with?");
        }
    }

    public String encryptText(String plain) {
        return plain == null ? null : Base64.getEncoder().encodeToString(encrypt(plain.getBytes(StandardCharsets.UTF_8)));
    }

    public String decryptText(String stored) {
        return stored == null ? null : new String(decrypt(Base64.getDecoder().decode(stored)), StandardCharsets.UTF_8);
    }

    private void requireKey() {
        if (key == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Worker details aren't set up on the server yet (app.workers.data-secret).");
        }
    }
}
