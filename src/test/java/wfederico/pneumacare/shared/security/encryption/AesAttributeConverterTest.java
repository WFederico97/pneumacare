package wfederico.pneumacare.shared.security.encryption;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit tests for {@link AesAttributeConverter}.
 *
 * <p>Verifies round-trip correctness, non-determinism (random IV),
 * Base64 output format, and null passthrough. No Spring context loaded.
 */
class AesAttributeConverterTest {

    // 32 zero-bytes: AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA= (43 A's + =)
    private static final String TEST_KEY_B64 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    private AesAttributeConverter converter;

    @BeforeEach
    void setUp() {
        byte[] keyBytes = Base64.getDecoder().decode(TEST_KEY_B64);
        converter = new AesAttributeConverter(new SecretKeySpec(keyBytes, "AES"));
    }

    // -------------------------------------------------------------------------
    // Round-trip
    // -------------------------------------------------------------------------

    @Test
    void encryptThenDecryptReturnsOriginalValue() {
        String plaintext = "Juan Pérez";
        String encrypted = converter.convertToDatabaseColumn(plaintext);
        String decrypted = converter.convertToEntityAttribute(encrypted);

        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void roundTripPreservesUnicodeCharacters() {
        String plaintext = "Ñoño José Ángel Güitrón";
        String decrypted = converter.convertToEntityAttribute(
                converter.convertToDatabaseColumn(plaintext));

        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void roundTripPreservesNumericNationalId() {
        String nationalId = "12345678";
        String decrypted = converter.convertToEntityAttribute(
                converter.convertToDatabaseColumn(nationalId));

        assertThat(decrypted).isEqualTo(nationalId);
    }

    // -------------------------------------------------------------------------
    // Output format
    // -------------------------------------------------------------------------

    @Test
    void encryptedValueIsNotEqualToPlaintext() {
        String plaintext  = "Juan";
        String encrypted  = converter.convertToDatabaseColumn(plaintext);

        assertThat(encrypted).isNotEqualTo(plaintext);
    }

    @Test
    void encryptedValueIsValidBase64() {
        String encrypted = converter.convertToDatabaseColumn("test");

        assertThatCode(() -> Base64.getDecoder().decode(encrypted)).doesNotThrowAnyException();
    }

    @Test
    void encryptedByteLengthIsGreaterThanPlaintext() {
        String plaintext  = "Juan";
        String encrypted  = converter.convertToDatabaseColumn(plaintext);
        byte[] decoded    = Base64.getDecoder().decode(encrypted);

        // Minimum: IV(12) + ciphertext(>=1) + tag(16) > plaintext.length()
        assertThat(decoded.length).isGreaterThan(plaintext.length());
    }

    // -------------------------------------------------------------------------
    // Non-determinism (random IV per write)
    // -------------------------------------------------------------------------

    @Test
    void sameInputProducesDifferentCiphertextEachTime() {
        String plaintext = "Juan";
        String enc1 = converter.convertToDatabaseColumn(plaintext);
        String enc2 = converter.convertToDatabaseColumn(plaintext);

        // AES-GCM random IV guarantees non-deterministic output
        assertThat(enc1).isNotEqualTo(enc2);
    }

    // -------------------------------------------------------------------------
    // Null passthrough
    // -------------------------------------------------------------------------

    @Test
    void nullPlaintextReturnsNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void nullEncryptedValueReturnsNull() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }
}
