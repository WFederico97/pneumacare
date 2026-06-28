package wfederico.pneumacare.shared.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class CookieBearerTokenResolverTest {

    private final CookieBearerTokenResolver resolver = new CookieBearerTokenResolver("PNMC_AT");

    @Test
    void resolve_cookiePresent_returnsTokenValue() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("PNMC_AT", "the.jwt.value"));

        assertThat(resolver.resolve(request)).isEqualTo("the.jwt.value");
    }

    @Test
    void resolve_noCookies_returnsNull() {
        assertThat(resolver.resolve(new MockHttpServletRequest())).isNull();
    }

    @Test
    void resolve_differentCookie_returnsNull() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("OTHER", "x"));

        assertThat(resolver.resolve(request)).isNull();
    }
}
