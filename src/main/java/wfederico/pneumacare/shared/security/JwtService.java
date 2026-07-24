package wfederico.pneumacare.shared.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Issues self-signed HS256 JWTs for authenticated users.
 *
 * <p>Claims: {@code sub} = user UUID, {@code roles} = authority names,
 * {@code icu_id} = the ICU the session is scoped to, plus {@code iat}/{@code exp}
 * (lifetime from {@link JwtProperties#getExpiration()}).
 *
 * <p>Until users are individually associated with an ICU, every token carries the
 * single configured default ICU ({@code app.security.default-icu-id}) — the same
 * ICU seeded by Flyway V26 / the dev {@code IcuTestDataSeeder}. Consumers such as
 * {@code IcuBedService} read this claim to scope queries.
 */
@Service
public class JwtService {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;
    private final String defaultIcuId;

    public JwtService(JwtEncoder jwtEncoder,
                      JwtProperties properties,
                      @Value("${app.security.default-icu-id:cccccccc-0000-0000-0000-000000000001}") String defaultIcuId) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.defaultIcuId = defaultIcuId;
    }

    public String issueToken(UserPrincipal principal) {
        Instant now = Instant.now();
        List<String> roles = principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(principal.getId().toString())
                .claim("roles", roles)
                .claim("icu_id", defaultIcuId)
                .issuedAt(now)
                .expiresAt(now.plus(properties.getExpiration()))
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
