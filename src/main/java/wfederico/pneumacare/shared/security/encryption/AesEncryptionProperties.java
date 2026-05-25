package wfederico.pneumacare.shared.security.encryption;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the AES encryption key from {@code app.security.encryption.*} properties.
 *
 * <p>The key must be supplied via the {@code AES_SECRET_KEY} environment variable.
 * The application will refuse to start if the value is absent or invalid
 * (see {@link AesEncryptionConfig#aesSecretKeySpec}).
 *
 * <p>Generate a valid key with:
 * <pre>{@code openssl rand -base64 32}</pre>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.security.encryption")
public class AesEncryptionProperties {

    /**
     * Base64-encoded AES-256 secret key (32 bytes / 256 bits).
     * Must be supplied via the {@code AES_SECRET_KEY} environment variable.
     * <strong>Never hardcode this value in source.</strong>
     */
    private String aesSecretKey;
}
