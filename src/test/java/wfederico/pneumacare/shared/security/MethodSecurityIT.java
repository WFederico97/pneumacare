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
import org.springframework.http.MediaType;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end role enforcement + CSRF under the non-dev chain. Disabled by house
 * convention; run individually with {@code mvnw.cmd test -Dtest=MethodSecurityIT}
 * (Docker required).
 */
@Disabled("Integration test — run individually; requires Docker")
@SpringBootTest(properties = {
        "app.security.encryption.aes-secret-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "app.security.jwt.secret=0123456789abcdef0123456789abcdef",
        "app.security.bootstrap-admin.enabled=false",
        "spring.docker.compose.enabled=false"
})
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@ActiveProfiles("staging")
class MethodSecurityIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @MockitoBean private StringRedisTemplate redisTemplate;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        ValueOperations<String, String> ops = Mockito.mock(ValueOperations.class);
        Mockito.when(redisTemplate.opsForValue()).thenReturn(ops);
        Mockito.when(ops.increment(anyString())).thenReturn(1L);

        userRepository.deleteAll();
        saveUser("therapist", Role.ROLE_THERAPIST);
        saveUser("admin", Role.ROLE_ADMIN);
    }

    private void saveUser(String username, Role role) {
        userRepository.save(UserJpaEntity.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode("secret"))
                .displayName(username)
                .enabled(true)
                .roles(EnumSet.of(role))
                .build());
    }

    private MvcResult login(String username) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andReturn();
    }

    @Test
    void therapistCanReadOwnRoleEndpoint() throws Exception {
        Cookie jwt = login("therapist").getResponse().getCookie("PNMC_AT");
        mockMvc.perform(get("/api/v1/shifts/active").cookie(jwt))
                .andExpect(status().isOk());
    }

    @Test
    void therapistCannotOpenShift_returns403() throws Exception {
        MvcResult result = login("therapist");
        Cookie jwt = result.getResponse().getCookie("PNMC_AT");
        Cookie xsrf = result.getResponse().getCookie("XSRF-TOKEN");

        // shifts POST requires CHIEF_OF_GUARD; therapist is denied even with a valid CSRF token.
        mockMvc.perform(post("/api/v1/shifts")
                        .cookie(jwt, xsrf)
                        .header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"icuId\":\"cccccccc-0000-0000-0000-000000000001\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminInheritsTherapistEndpoint() throws Exception {
        Cookie jwt = login("admin").getResponse().getCookie("PNMC_AT");
        mockMvc.perform(get("/api/v1/shifts/active").cookie(jwt))
                .andExpect(status().isOk());
    }

    @Test
    void mutatingRequestWithoutCsrfToken_returns403() throws Exception {
        Cookie jwt = login("admin").getResponse().getCookie("PNMC_AT");
        mockMvc.perform(post("/api/v1/shifts")
                        .cookie(jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"icuId\":\"cccccccc-0000-0000-0000-000000000001\"}"))
                .andExpect(status().isForbidden());
    }
}
