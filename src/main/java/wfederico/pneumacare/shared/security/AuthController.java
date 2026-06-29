package wfederico.pneumacare.shared.security;

import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import wfederico.pneumacare.shared.exception.BusinessLayerException;
import wfederico.pneumacare.shared.web.ApiResponseBase;
import wfederico.pneumacare.shared.web.dto.LoginRequest;
import wfederico.pneumacare.shared.web.dto.LoginResponse;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Authentication endpoints. Issues the JWT only as an HttpOnly cookie plus a
 * readable XSRF-TOKEN cookie; the body returns non-sensitive profile data only.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    public AuthController(AuthenticationManager authenticationManager,
                         JwtService jwtService,
                         JwtProperties jwtProperties) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
    }

    @PreAuthorize("permitAll()")
    @PostMapping("/login")
    public ResponseEntity<ApiResponseBase<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        } catch (AuthenticationException ex) {
            throw new BusinessLayerException("Credenciales inválidas", HttpStatus.UNAUTHORIZED);
        }

        SecurityContextHolder.getContext().setAuthentication(authentication);
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

        String token = jwtService.issueToken(principal);
        List<String> roles = principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        ResponseCookie jwtCookie = buildCookie(jwtProperties.getCookieName(), token, true, jwtProperties.getExpiration());
        ResponseCookie xsrfCookie = buildCookie(jwtProperties.getXsrfCookieName(),
                UUID.randomUUID().toString(), false, jwtProperties.getExpiration());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, jwtCookie.toString())
                .header(HttpHeaders.SET_COOKIE, xsrfCookie.toString())
                .body(ApiResponseBase.<LoginResponse>builder()
                        .status(HttpStatus.OK.value())
                        .message("Autenticación exitosa")
                        .data(new LoginResponse(principal.getDisplayName(), roles))
                        .traceId(MDC.get("traceId"))
                        .build());
    }

    @PreAuthorize("permitAll()")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponseBase<Void>> logout() {
        ResponseCookie jwtCookie = buildCookie(jwtProperties.getCookieName(), "", true, Duration.ZERO);
        ResponseCookie xsrfCookie = buildCookie(jwtProperties.getXsrfCookieName(), "", false, Duration.ZERO);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, jwtCookie.toString())
                .header(HttpHeaders.SET_COOKIE, xsrfCookie.toString())
                .body(ApiResponseBase.<Void>builder()
                        .status(HttpStatus.OK.value())
                        .message("Sesión cerrada")
                        .traceId(MDC.get("traceId"))
                        .build());
    }

    private ResponseCookie buildCookie(String name, String value, boolean httpOnly, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(httpOnly)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(maxAge)
                .build();
    }
}
