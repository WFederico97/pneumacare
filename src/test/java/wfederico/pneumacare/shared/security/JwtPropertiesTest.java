package wfederico.pneumacare.shared.security;

import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtPropertiesTest {

    private JwtProperties withSecret(String secret) {
        JwtProperties props = new JwtProperties();
        props.setSecret(secret);
        return props;
    }

    @Test
    void secretKey_validSecret_returnsHmacSha256Key() {
        JwtProperties props = withSecret("0123456789abcdef0123456789abcdef"); // 32 bytes

        SecretKey key = props.getSecretKey();

        assertThat(key.getAlgorithm()).isEqualTo("HmacSHA256");
        assertThat(key.getEncoded()).hasSize(32);
    }

    @Test
    void secretKey_tooShort_throws() {
        JwtProperties props = withSecret("short");

        assertThatThrownBy(props::getSecretKey)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("256-bit");
    }

    @Test
    void secretKey_null_throws() {
        assertThatThrownBy(() -> withSecret(null).getSecretKey())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void defaults_areEightHourExpiryAndExpectedCookieNames() {
        JwtProperties props = new JwtProperties();

        assertThat(props.getExpiration()).isEqualTo(Duration.ofHours(8));
        assertThat(props.getCookieName()).isEqualTo("PNMC_AT");
        assertThat(props.getXsrfCookieName()).isEqualTo("XSRF-TOKEN");
    }
}
