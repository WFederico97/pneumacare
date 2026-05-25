package wfederico.pneumacare.shared.security.encryption;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

/**
 * Provides the AES-256 {@link SecretKeySpec} bean used by {@link AesAttributeConverter}.
 *
 * <h3>Fail-fast security check</h3>
 * This configuration bean validates the AES key at Spring context startup.
 * The application will <strong>refuse to start</strong> with a clear
 * {@link IllegalStateException} if the key is:
 * <ul>
 *   <li>missing (null or blank) — {@code AES_SECRET_KEY} env var not set</li>
 *   <li>not valid Base64</li>
 *   <li>not exactly 32 bytes (256 bits) when decoded</li>
 * </ul>
 *
 * <p>This enforces Acceptance Criterion 3: startup is aborted when the key is absent,
 * protecting patient PII from being stored unencrypted (Law 25.326).
 */
@Configuration
@EnableConfigurationProperties(AesEncryptionProperties.class)
public class AesEncryptionConfig {

    /**
     * Creates the AES-256 {@link SecretKeySpec} from the Base64-encoded key in
     * {@code app.security.encryption.aes-secret-key}.
     *
     * @param props AES encryption configuration properties
     * @return a ready-to-use {@link SecretKeySpec} for AES operations
     * @throws IllegalStateException if the key is missing, not valid Base64,
     *                               or does not decode to exactly 32 bytes
     */
    @Bean
    public SecretKeySpec aesSecretKeySpec(AesEncryptionProperties props) {
        String encodedKey = props.getAesSecretKey();

        if (encodedKey == null || encodedKey.isBlank()) {
            throw new IllegalStateException(
                    "Security configuration error: AES_SECRET_KEY is not configured. " +
                    "Set the 'app.security.encryption.aes-secret-key' property via the " +
                    "AES_SECRET_KEY environment variable. " +
                    "Application startup aborted to protect patient PII (Law 25.326). " +
                    "Generate a valid key with: openssl rand -base64 32");
        }

        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(encodedKey);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "Security configuration error: AES_SECRET_KEY is not valid Base64. " +
                    "Generate a valid key with: openssl rand -base64 32", ex);
        }

        if (keyBytes.length != 32) {
            throw new IllegalStateException(
                    "Security configuration error: AES_SECRET_KEY must decode to exactly " +
                    "32 bytes (256 bits) for AES-256. The configured key decodes to " +
                    keyBytes.length + " bytes. " +
                    "Generate a valid key with: openssl rand -base64 32");
        }

        return new SecretKeySpec(keyBytes, "AES");
    }
}
