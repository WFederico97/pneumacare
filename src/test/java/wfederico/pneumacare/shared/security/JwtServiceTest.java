package wfederico.pneumacare.shared.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private final JwtProperties props = props();
    private final SecretKey key = props.getSecretKey();
    private final JwtService service = new JwtService(new NimbusJwtEncoder(new ImmutableSecret<>(key)), props);
    private final JwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();

    private static JwtProperties props() {
        JwtProperties p = new JwtProperties();
        p.setSecret("0123456789abcdef0123456789abcdef");
        p.setExpiration(Duration.ofHours(8));
        return p;
    }

    @Test
    void issueToken_decodesWithSameKey_andCarriesExpectedClaims() {
        UUID id = UUID.randomUUID();
        UserPrincipal principal = new UserPrincipal(
                id, "jdoe", "$2a$10$hash", "J. Doe", true,
                List.of(new SimpleGrantedAuthority("ROLE_THERAPIST")));

        String token = service.issueToken(principal);
        Jwt decoded = decoder.decode(token);

        assertThat(decoded.getSubject()).isEqualTo(id.toString());
        assertThat(decoded.getClaimAsStringList("roles")).containsExactly("ROLE_THERAPIST");
        assertThat(decoded.getIssuedAt()).isNotNull();
        assertThat(decoded.getExpiresAt()).isNotNull();
        assertThat(Duration.between(decoded.getIssuedAt(), decoded.getExpiresAt()))
                .isEqualTo(Duration.ofHours(8));
        assertThat(decoded.getHeaders().get("alg")).hasToString("HS256");
    }
}
