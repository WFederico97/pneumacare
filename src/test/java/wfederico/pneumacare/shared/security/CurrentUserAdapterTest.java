package wfederico.pneumacare.shared.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CurrentUserAdapterTest {

    private static final String DEFAULT_ID = "eeeeeeee-0000-0000-0000-000000000001";

    private CurrentUserAdapter adapter() {
        CurrentUserAdapter adapter = new CurrentUserAdapter();
        ReflectionTestUtils.setField(adapter, "defaultUserId", DEFAULT_ID);
        return adapter;
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void currentUserId_authenticatedUuidPrincipal_returnsThatUuid() {
        UUID id = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(id.toString(), null, List.of()));

        assertThat(adapter().currentUserId()).isEqualTo(id);
    }

    @Test
    void currentUserId_noAuthentication_returnsConfiguredDefault() {
        assertThat(adapter().currentUserId()).isEqualTo(UUID.fromString(DEFAULT_ID));
    }
}
