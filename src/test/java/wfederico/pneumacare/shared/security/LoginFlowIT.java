package wfederico.pneumacare.shared.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import wfederico.pneumacare.TestcontainersConfiguration;
import wfederico.pneumacare.shared.security.user.Role;
import wfederico.pneumacare.shared.security.user.UserJpaEntity;
import wfederico.pneumacare.shared.security.user.UserRepository;

import java.util.EnumSet;

import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end login → cookie → validated request flow under the non-dev security
 * chain (symmetric cookie JWT). Disabled by house convention; run individually
 * with {@code mvnw.cmd test -Dtest=LoginFlowIT} (Docker required).
 *
 * <p>A garbage cookie is rejected with 401 while the issued cookie is accepted,
 * proving the symmetric decoder validates the token. Role-gated authorization is
 * exercised separately once the SCOPE→ROLE migration lands.
 */
@Disabled("Integration test — run individually; requires Docker")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.security.encryption.aes-secret-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                "app.security.jwt.secret=0123456789abcdef0123456789abcdef",
                "app.security.bootstrap-admin.enabled=false",
                "spring.docker.compose.enabled=false"
        }
)
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@ActiveProfiles("staging")
class LoginFlowIT {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ValueOperations<String, String> valueOps = Mockito.mock(ValueOperations.class);
        Mockito.when(redisTemplate.opsForValue()).thenReturn(valueOps);
        Mockito.when(valueOps.increment(anyString())).thenReturn(1L);

        userRepository.deleteAll();
        userRepository.save(UserJpaEntity.builder()
                .username("jdoe")
                .passwordHash(passwordEncoder.encode("secret"))
                .displayName("J. Doe")
                .enabled(true)
                .roles(EnumSet.of(Role.ROLE_THERAPIST))
                .build());
    }

    @Test
    void login_thenIssuedCookie_isAcceptedAndGarbageCookieRejected() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"jdoe\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("PNMC_AT"))
                .andReturn();

        Cookie jwtCookie = loginResult.getResponse().getCookie("PNMC_AT");

        // Valid cookie: the resource-server filter decodes it without rejecting the request.
        mockMvc.perform(get("/api/v1/identifier-types").cookie(jwtCookie))
                .andExpect(status().isOk());

        // Garbage cookie: a malformed token is rejected by the decoder.
        mockMvc.perform(get("/api/v1/identifier-types").cookie(new Cookie("PNMC_AT", "garbage")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_invalidPassword_returns401NoCookie() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("{\"username\":\"jdoe\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().doesNotExist("PNMC_AT"));
    }
}
