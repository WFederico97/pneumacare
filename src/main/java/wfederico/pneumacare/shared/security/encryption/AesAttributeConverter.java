package wfederico.pneumacare.shared.security.encryption;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * JPA {@link AttributeConverter} that transparently encrypts and decrypts PII
 * {@link String} fields using <strong>AES-256-GCM</strong>.
 *
 * <h2>Storage format</h2>
 * <pre>Base64( IV[12 bytes] || AES-GCM-Ciphertext+AuthTag )</pre>
 * The 12-byte IV is randomly generated on every write and prepended to the ciphertext.
 * The result is Base64-encoded to a printable string safe for {@code VARCHAR} / {@code TEXT}
 * columns.
 *
 * <h2>Security properties</h2>
 * <ul>
 *   <li>AES-256 — 256-bit key loaded from {@link AesEncryptionConfig}</li>
 *   <li>GCM mode — authenticated encryption; detects ciphertext tampering</li>
 *   <li>128-bit authentication tag</li>
 *   <li>Random IV per write — same plaintext produces different ciphertext each time
 *       (non-deterministic / IND-CPA secure)</li>
 * </ul>
 *
 * <h2>UNIQUE constraint caveat</h2>
 * Because the IV is random, two inserts of the same plaintext produce different
 * ciphertext values. A DB-level {@code UNIQUE} constraint on an encrypted column
 * <strong>cannot</strong> detect duplicate plaintexts, and the constraint has
 * been dropped from {@code national_id}.
 * Application-layer dedup via an equality query is equally impossible — the query
 * parameter would be re-encrypted to a fresh ciphertext, never matching stored rows.
 * Enforcing uniqueness requires a deterministic auxiliary column (e.g., an HMAC-SHA256
 * of the plaintext) with its own {@code UNIQUE} index.
 * <strong>This is currently deferred</strong> — duplicate national IDs are not
 * prevented by this implementation.
 *
 * <h2>Null handling</h2>
 * {@code null} inputs are passed through unchanged so that nullable columns work
 * without special casing in the entity.
 *
 * <p>Complies with Argentine Law 25.326 (Habeas Data / PII protection).
 *
 * @see AesEncryptionConfig
 * @see AesEncryptionProperties
 */
@Slf4j
@Converter
@Component
@RequiredArgsConstructor
public class AesAttributeConverter implements AttributeConverter<String, String> {

    static final String ALGORITHM       = "AES/GCM/NoPadding";
    static final int    IV_LENGTH_BYTES = 12;   // 96-bit IV — recommended for GCM
    static final int    TAG_LENGTH_BITS = 128;  // 128-bit authentication tag

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final SecretKeySpec secretKey;

    /**
     * Encrypts {@code plainText} and returns {@code Base64(IV || ciphertext+tag)}.
     *
     * @param plainText the plain-text PII value to encrypt; {@code null} is returned as-is
     * @return Base64-encoded encrypted value, or {@code null}
     * @throws PiiEncryptionException on any cryptographic failure
     */
    @Override
    public String convertToDatabaseColumn(String plainText) {
        if (plainText == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // Prepend IV so the decryptor can always extract it
            byte[] combined = new byte[IV_LENGTH_BYTES + ciphertext.length];
            System.arraycopy(iv,         0, combined, 0,               IV_LENGTH_BYTES);
            System.arraycopy(ciphertext, 0, combined, IV_LENGTH_BYTES, ciphertext.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception ex) {
            throw new PiiEncryptionException("Failed to encrypt PII field", ex);
        }
    }

    /**
     * Decrypts {@code encryptedBase64} (format: {@code Base64(IV || ciphertext+tag)}).
     *
     * @param encryptedBase64 the Base64-encoded encrypted value from the database;
     *                        {@code null} is returned as-is
     * @return the decrypted plain-text value, or {@code null}
     * @throws PiiEncryptionException on any cryptographic failure (wrong key, tampered data)
     */
    @Override
    public String convertToEntityAttribute(String encryptedBase64) {
        if (encryptedBase64 == null) {
            return null;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(encryptedBase64);
            int    minLen   = IV_LENGTH_BYTES + (TAG_LENGTH_BITS / Byte.SIZE);  // 12 + 16 = 28
            if (combined.length < minLen) {
                throw new PiiEncryptionException(
                        "Failed to decrypt PII field: stored value is too short or corrupt " +
                        "(got " + combined.length + " bytes, need at least " + minLen + ")");
            }
            byte[] iv         = Arrays.copyOfRange(combined, 0,               IV_LENGTH_BYTES);
            byte[] ciphertext = Arrays.copyOfRange(combined, IV_LENGTH_BYTES, combined.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BITS, iv));

            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (PiiEncryptionException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PiiEncryptionException("Failed to decrypt PII field", ex);
        }
    }
}
