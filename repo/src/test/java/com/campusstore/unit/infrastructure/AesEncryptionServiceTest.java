package com.campusstore.unit.infrastructure;

import com.campusstore.infrastructure.security.encryption.AesEncryptionService;
import com.campusstore.infrastructure.security.encryption.MasterKeyHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class AesEncryptionServiceTest {

    private AesEncryptionService encryptionService;
    private MasterKeyHolder masterKeyHolder;

    @BeforeEach
    void setUp() throws Exception {
        masterKeyHolder = mock(MasterKeyHolder.class);

        // Derive a test key
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        PBEKeySpec spec = new PBEKeySpec("test-passphrase".toCharArray(), "test-salt".getBytes(), 1000, 256);
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        SecretKey testKey = new SecretKeySpec(keyBytes, "AES");

        when(masterKeyHolder.getKey()).thenReturn(testKey);
        encryptionService = new AesEncryptionService(masterKeyHolder);
    }

    @Test
    void encryptDecrypt_roundTrip_success() {
        String plaintext = "Hello, Campus Store!";
        byte[] encrypted = encryptionService.encrypt(plaintext);
        assertNotNull(encrypted);
        assertTrue(encrypted.length > 0);

        String decrypted = encryptionService.decrypt(encrypted);
        assertEquals(plaintext, decrypted);
    }

    @Test
    void encryptDecrypt_phoneNumber() {
        String phone = "+1-555-123-4567";
        byte[] encrypted = encryptionService.encrypt(phone);
        String decrypted = encryptionService.decrypt(encrypted);
        assertEquals(phone, decrypted);
    }

    @Test
    void encryptDecrypt_emailAddress() {
        String email = "student@campus.edu";
        byte[] encrypted = encryptionService.encrypt(email);
        String decrypted = encryptionService.decrypt(encrypted);
        assertEquals(email, decrypted);
    }

    @Test
    void encryptDecrypt_address() {
        String address = "123 Main St, Springfield, IL 62704";
        byte[] encrypted = encryptionService.encrypt(address);
        String decrypted = encryptionService.decrypt(encrypted);
        assertEquals(address, decrypted);
    }

    @Test
    void encrypt_null_returnsNull() {
        assertNull(encryptionService.encrypt(null));
    }

    @Test
    void decrypt_null_returnsNull() {
        assertNull(encryptionService.decrypt(null));
    }

    @Test
    void encrypt_emptyString() {
        byte[] encrypted = encryptionService.encrypt("");
        assertNotNull(encrypted);
        assertEquals("", encryptionService.decrypt(encrypted));
    }

    @Test
    void encrypt_sameInput_differentOutput() {
        // Each encryption should produce different ciphertext due to random IV
        String plaintext = "test data";
        byte[] encrypted1 = encryptionService.encrypt(plaintext);
        byte[] encrypted2 = encryptionService.encrypt(plaintext);

        assertNotNull(encrypted1);
        assertNotNull(encrypted2);
        assertFalse(java.util.Arrays.equals(encrypted1, encrypted2),
                "Same plaintext should produce different ciphertext (random IV)");

        // But both should decrypt to the same value
        assertEquals(plaintext, encryptionService.decrypt(encrypted1));
        assertEquals(plaintext, encryptionService.decrypt(encrypted2));
    }

    @Test
    void encrypt_unicodeContent() {
        String unicode = "日本語テスト 🎓📚";
        byte[] encrypted = encryptionService.encrypt(unicode);
        String decrypted = encryptionService.decrypt(encrypted);
        assertEquals(unicode, decrypted);
    }

    @Test
    void decrypt_tamperedData_throwsException() {
        byte[] encrypted = encryptionService.encrypt("test");
        assertNotNull(encrypted);

        // Tamper with the ciphertext
        encrypted[encrypted.length - 1] ^= 0xFF;

        assertThrows(RuntimeException.class, () -> encryptionService.decrypt(encrypted));
    }

    @Test
    void extractLast4_normalPhone() {
        assertEquals("4567", AesEncryptionService.extractLast4("+1-555-123-4567"));
    }

    @Test
    void extractLast4_digitsOnly() {
        assertEquals("1234", AesEncryptionService.extractLast4("1234"));
    }

    @Test
    void extractLast4_shortString() {
        assertEquals("12", AesEncryptionService.extractLast4("12"));
    }

    @Test
    void extractLast4_null() {
        assertNull(AesEncryptionService.extractLast4(null));
    }

    @Test
    void extractLast4_longNumber() {
        assertEquals("7890", AesEncryptionService.extractLast4("1234567890"));
    }
}
