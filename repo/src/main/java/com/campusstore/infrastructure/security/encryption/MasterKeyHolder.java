package com.campusstore.infrastructure.security.encryption;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.spec.KeySpec;

/**
 * Holds the AES-256 master key in volatile memory only.
 * The key is derived from MASTER_KEY_PASSPHRASE env var using PBKDF2.
 * It is NEVER written to disk, properties files, or logs.
 */
@Component
public class MasterKeyHolder {

    private static final Logger log = LoggerFactory.getLogger(MasterKeyHolder.class);
    private static final String KEY_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String AES_ALGORITHM = "AES";
    private static final int KEY_LENGTH = 256;

    @Value("${campusstore.master-key-passphrase}")
    private volatile String passphrase;

    @Value("${campusstore.encryption.salt}")
    private String salt;

    @Value("${campusstore.encryption.iterations}")
    private int iterations;

    private volatile SecretKey secretKey;

    @PostConstruct
    public void init() {
        if (passphrase == null || passphrase.isBlank()) {
            throw new IllegalStateException(
                    "MASTER_KEY_PASSPHRASE environment variable is required but not set. "
                    + "Application cannot start without the encryption master key."
            );
        }

        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(KEY_ALGORITHM);
            KeySpec spec = new PBEKeySpec(
                    passphrase.toCharArray(),
                    salt.getBytes(),
                    iterations,
                    KEY_LENGTH
            );
            byte[] keyBytes = factory.generateSecret(spec).getEncoded();
            this.secretKey = new SecretKeySpec(keyBytes, AES_ALGORITHM);

            // Clear passphrase from memory
            this.passphrase = null;

            log.info("AES-256 master key derived successfully from passphrase (PBKDF2, {} iterations)", iterations);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive AES-256 key from master passphrase", e);
        }
    }

    public SecretKey getKey() {
        if (secretKey == null) {
            throw new IllegalStateException("Master key not initialized");
        }
        return secretKey;
    }

    @Override
    public String toString() {
        return "MasterKeyHolder{initialized=" + (secretKey != null) + "}";
    }
}
