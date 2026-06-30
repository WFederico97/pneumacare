package wfederico.pneumacare.shared.security.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.security.oauth2.client.autoconfigure.servlet.OAuth2ClientWebSecurityAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import wfederico.pneumacare.shared.security.SecurityConfig;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@link UserAdminController} + {@link UserAdminService}.
 * Runs under the {@code dev} chain, where {@code DevAuthInjectionFilter} injects
 * a {@code ROLE_ADMIN} principal — so authorization passes and the admin-only
 * escalation guards are exercised on the happy path.
 */
@WebMvcTest(
        value = UserAdminController.class,
        excludeAutoConfiguration = {
                OAuth2ResourceServerAutoConfiguration.class,
                OAuth2ClientAutoConfiguration.class,
                OAuth2ClientWebSecurityAutoConfiguration.class
        })
@Import({SecurityConfig.class, UserAdminService.class})
@ActiveProfiles("dev")
class UserAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void stubRedis() {
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString())).thenReturn(1L);
    }

    private UserJpaEntity user(UUID id, String username, Role... roles) {
        return UserJpaEntity.builder()
                .id(id)
                .username(username)
                .passwordHash("$2a$10$hash")
                .displayName(username + " Name")
                .enabled(true)
                .roles(EnumSet.copyOf(List.of(roles)))
                .build();
    }

    @Test
    void list_returnsUsers() throws Exception {
        when(userRepository.findAll()).thenReturn(List.of(
                user(UUID.randomUUID(), "alice", Role.ROLE_THERAPIST),
                user(UUID.randomUUID(), "bob", Role.ROLE_CHIEF_OF_GUARD)));

        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void create_valid_returns201() throws Exception {
        when(userRepository.findByUsername("nurse")).thenReturn(Optional.empty());
        when(userRepository.save(any(UserJpaEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/v1/users")
                        .contentType("application/json")
                        .content("{\"username\":\"nurse\",\"password\":\"secret12\",\"displayName\":\"N. Urse\",\"roles\":[\"ROLE_THERAPIST\"],\"enabled\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.username").value("nurse"))
                .andExpect(jsonPath("$.data.roles[0]").value("ROLE_THERAPIST"));
    }

    @Test
    void create_duplicateUsername_returns409() throws Exception {
        when(userRepository.findByUsername("nurse"))
                .thenReturn(Optional.of(user(UUID.randomUUID(), "nurse", Role.ROLE_THERAPIST)));

        mockMvc.perform(post("/api/v1/users")
                        .contentType("application/json")
                        .content("{\"username\":\"nurse\",\"password\":\"secret12\",\"displayName\":\"N. Urse\",\"roles\":[\"ROLE_THERAPIST\"],\"enabled\":true}"))
                .andExpect(status().isConflict());
    }

    @Test
    void get_unknown_returns404() throws Exception {
        when(userRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/users/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_changesProfileRolesAndPassword_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id))
                .thenReturn(Optional.of(user(id, "nurse", Role.ROLE_THERAPIST)));
        when(userRepository.save(any(UserJpaEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(put("/api/v1/users/" + id)
                        .contentType("application/json")
                        .content("{\"displayName\":\"Updated Name\",\"roles\":[\"ROLE_CHIEF_OF_GUARD\"],\"enabled\":false,\"password\":\"newsecret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("Updated Name"))
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.roles[0]").value("ROLE_CHIEF_OF_GUARD"));
    }

    @Test
    void disable_existingUser_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        UserJpaEntity target = user(id, "nurse", Role.ROLE_THERAPIST);
        when(userRepository.findById(id)).thenReturn(Optional.of(target));
        when(userRepository.save(any(UserJpaEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(delete("/api/v1/users/" + id))
                .andExpect(status().isOk());
    }
}
