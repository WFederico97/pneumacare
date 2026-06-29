package wfederico.pneumacare.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Dev-only: injects a ROLE_ADMIN principal (with a UUID name for actor
 * attribution) when no authentication is present, keeping local dev open while
 * method security is active. Inert under {@code @WithMockUser}/{@code @WithAnonymousUser}
 * (which pre-populate a non-null context).
 */
public class DevAuthInjectionFilter extends OncePerRequestFilter {

    private final String devUserId;

    public DevAuthInjectionFilter(String devUserId) {
        this.devUserId = devUserId;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    devUserId, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        filterChain.doFilter(request, response);
    }
}
