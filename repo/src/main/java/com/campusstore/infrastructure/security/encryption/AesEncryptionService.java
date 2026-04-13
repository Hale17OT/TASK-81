package com.campusstore.infrastructure.security.encryption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;

/**
 * AES-256-GCM authenticated encryption service.
 * Each encrypted value stores: IV (12 bytes) + ciphertext + auth tag (16 bytes).
 */
@Service
public class AesEncryptionService {

    private static final Logger log = LoggerFactory.getLogger(AesEncryptionService.class);
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final MasterKeyHolder masterKeyHolder;
    private final SecureRandom secureRandom;

    public AesEncryptionService(MasterKeyHolder masterKeyHolder) {
        this.masterKeyHolder = masterKeyHolder;
        this.secureRandom = new SecureRandom();
    }

    /**
     * Encrypts plaintext string to byte array.
     * Returns: IV (12 bytes) || ciphertext || GCM tag (16 bytes)
     */
    public byte[] encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, masterKeyHolder.getKey(), parameterSpec);

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(GCM_IV_LENGTH + ciphertext.length);
            buffer.put(iv);
            buffer.put(ciphertext);
            return buffer.array();
        } catch (Exception e) {
            log.error("Encryption failed", e);
            throw new RuntimeException("Encryption failed", e);
        }
    }

    /**
     * Decrypts byte array back to plaintext string.
     * Input format: IV (12 bytes) || ciphertext || GCM tag (16 bytes)
     */
    public String decrypt(byte[] encryptedData) {
        if (encryptedData == null) {
            return null;
        }
        try {
            ByteBuffer buffer = ByteBuffer.wrap(encryptedData);

            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);

            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, masterKeyHolder.getKey(), parameterSpec);

            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Decryption failed", e);
            throw new RuntimeException("Decryption failed", e);
        }
    }

    /**
     * Extracts the last 4 characters from a string for masking purposes.
     * Used for phone numbers before encryption.
     */
    public static String extractLast4(String value) {
        if (value == null || value.length() < 4) {
            return value;
        }
        String digitsOnly = value.replaceAll("[^0-9]", "");
        if (digitsOnly.length() < 4) {
            return digitsOnly;
        }
        return digitsOnly.substring(digitsOnly.length() - 4);
    }
}
