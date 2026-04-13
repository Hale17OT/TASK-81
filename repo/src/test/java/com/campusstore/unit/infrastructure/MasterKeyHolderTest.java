package com.campusstore.unit.infrastructure;

import com.campusstore.infrastructure.security.encryption.MasterKeyHolder;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
class MasterKeyHolderTest {

    private MasterKeyHolder createHolder(String passphrase, String salt, int iterations) throws Exception {
        MasterKeyHolder holder = new MasterKeyHolder();

        setField(holder, "passphrase", passphrase);
        setField(holder, "salt", salt);
        setField(holder, "iterations", iterations);

        return holder;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    void init_withValidPassphrase_derivesKey() throws Exception {
        MasterKeyHolder holder = createHolder("my-secure-passphrase", "test-salt", 1000);

        holder.init();

        assertThat(holder.getKey()).isNotNull();
        assertThat(holder.getKey().getAlgorithm()).isEqualTo("AES");
        assertThat(holder.getKey().getEncoded()).hasSize(32); // 256 bits = 32 bytes
    }

    @Test
    void init_withNullPassphrase_throwsIllegalStateException() throws Exception {
        MasterKeyHolder holder = createHolder(null, "test-salt", 1000);

        assertThatThrownBy(holder::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MASTER_KEY_PASSPHRASE");
    }

    @Test
    void init_withBlankPassphrase_throwsIllegalStateException() throws Exception {
        MasterKeyHolder holder = createHolder("   ", "test-salt", 1000);

        assertThatThrownBy(holder::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MASTER_KEY_PASSPHRASE");
    }

    @Test
    void init_withEmptyPassphrase_throwsIllegalStateException() throws Exception {
        MasterKeyHolder holder = createHolder("", "test-salt", 1000);

        assertThatThrownBy(holder::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MASTER_KEY_PASSPHRASE");
    }

    @Test
    void getKey_afterInit_returnsValidKey() throws Exception {
        MasterKeyHolder holder = createHolder("test-passphrase", "test-salt", 1000);

        holder.init();

        assertThat(holder.getKey()).isNotNull();
        assertThat(holder.getKey().getEncoded().length).isEqualTo(32);
    }

    @Test
    void getKey_beforeInit_throwsIllegalStateException() {
        MasterKeyHolder holder = new MasterKeyHolder();

        assertThatThrownBy(holder::getKey)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Master key not initialized");
    }

    @Test
    void init_clearPassphraseFromMemory() throws Exception {
        MasterKeyHolder holder = createHolder("secret-passphrase", "test-salt", 1000);

        holder.init();

        // After init, passphrase field should be null (cleared for security)
        Field passphraseField = MasterKeyHolder.class.getDeclaredField("passphrase");
        passphraseField.setAccessible(true);
        assertThat(passphraseField.get(holder)).isNull();
    }

    @Test
    void init_differentPassphrases_produceDifferentKeys() throws Exception {
        MasterKeyHolder holder1 = createHolder("passphrase-one", "test-salt", 1000);
        MasterKeyHolder holder2 = createHolder("passphrase-two", "test-salt", 1000);

        holder1.init();
        holder2.init();

        assertThat(holder1.getKey().getEncoded()).isNotEqualTo(holder2.getKey().getEncoded());
    }

    @Test
    void init_samePassphrase_producesSameKey() throws Exception {
        MasterKeyHolder holder1 = createHolder("same-passphrase", "test-salt", 1000);
        MasterKeyHolder holder2 = createHolder("same-passphrase", "test-salt", 1000);

        holder1.init();
        holder2.init();

        assertThat(holder1.getKey().getEncoded()).isEqualTo(holder2.getKey().getEncoded());
    }
}
