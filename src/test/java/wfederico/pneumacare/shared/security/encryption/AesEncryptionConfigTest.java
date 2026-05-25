package wfederico.pneumacare.shared.security.encryption;

import org.junit.jupiter.api.Test;

import javax.crypto.spec.SecretKeySpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link AesEncryptionConfig}.
 *
 * <p>Covers Acceptance Criterion 3: the application refuses to start when
 * {@code AES_SECRET_KEY} is missing, not valid Base64, or the wrong length.
 * These are pure unit tests — no Spring context is loaded.
 */
class AesEncryptionConfigTest {

    private final AesEncryptionConfig config = new AesEncryptionConfig();

    // -------------------------------------------------------------------------
    // AC3 — Startup failure scenarios
    // -------------------------------------------------------------------------

    @Test
    void throwsWhenKeyIsNull() {
        AesEncryptionProperties props = new AesEncryptionProperties();
        props.setAesSecretKey(null);

        assertThatThrownBy(() -> config.aesSecretKeySpec(props))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AES_SECRET_KEY is not configured");
    }

    @Test
    void throwsWhenKeyIsBlank() {
        AesEncryptionProperties props = new AesEncryptionProperties();
        props.setAesSecretKey("   ");

        assertThatThrownBy(() -> config.aesSecretKeySpec(props))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AES_SECRET_KEY is not configured");
    }

    @Test
    void throwsWhenKeyIsNotValidBase64() {
        AesEncryptionProperties props = new AesEncryptionProperties();
        props.setAesSecretKey("not-valid-base64!!!");

        assertThatThrownBy(() -> config.aesSecretKeySpec(props))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not valid Base64");
    }

    @Test
    void throwsWhenKeyDecodesToFewerThan32Bytes() {
        // 16 zero-bytes in Base64 = AAAAAAAAAAAAAAAAAAAAAA== (22 A's + ==)
        AesEncryptionProperties props = new AesEncryptionProperties();
        props.setAesSecretKey("AAAAAAAAAAAAAAAAAAAAAA==");

        assertThatThrownBy(() -> config.aesSecretKeySpec(props))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void throwsWhenKeyDecodesToMoreThan32Bytes() {
        // 48 zero-bytes in Base64 = AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA
        AesEncryptionProperties props = new AesEncryptionProperties();
        props.setAesSecretKey("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");

        assertThatThrownBy(() -> config.aesSecretKeySpec(props))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    // -------------------------------------------------------------------------
    // Happy path
    // -------------------------------------------------------------------------

    @Test
    void createsKeySpecForValid32ByteKey() {
        // 32 zero-bytes in Base64 = AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA= (43 A's + =)
        AesEncryptionProperties props = new AesEncryptionProperties();
        props.setAesSecretKey("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");

        SecretKeySpec keySpec = config.aesSecretKeySpec(props);

        assertThat(keySpec).isNotNull();
        assertThat(keySpec.getAlgorithm()).isEqualTo("AES");
        assertThat(keySpec.getEncoded()).hasSize(32);
    }
}
