package wfederico.pneumacare.shared.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Binds the self-issued JWT settings from {@code app.security.jwt.*}.
 *
 * <p>{@code secret} is the HS256 signing key, sourced from the
 * {@code PNMC_JWT_SECRET} environment variable in staging/prod. It must be at
 * least 256 bits (32 bytes). <strong>Never hardcode it in source.</strong>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.security.jwt")
public class JwtProperties {

    /** HS256 signing secret (env-supplied). Minimum 32 bytes / 256 bits. */
    private String secret;

    /** Token lifetime; also the auth-cookie Max-Age. */
    private Duration expiration = Duration.ofHours(8);

    /** Name of the HttpOnly cookie carrying the JWT. */
    private String cookieName = "PNMC_AT";

    /** Name of the readable double-submit CSRF cookie. */
    private String xsrfCookieName = "XSRF-TOKEN";

    /**
     * @return the signing secret as an HMAC-SHA256 key.
     * @throws IllegalStateException if the secret is missing or shorter than 256 bits.
     */
    public SecretKey getSecretKey() {
        if (secret == null) {
            throw new IllegalStateException("app.security.jwt.secret is not configured");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("app.security.jwt.secret must be at least 256-bit (32 bytes)");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}
