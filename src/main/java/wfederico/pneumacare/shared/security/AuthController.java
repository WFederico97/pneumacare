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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import wfederico.pneumacare.shared.exception.BusinessLayerException;
import wfederico.pneumacare.shared.security.user.Role;
import wfederico.pneumacare.shared.security.user.UserJpaEntity;
import wfederico.pneumacare.shared.security.user.UserRepository;
import wfederico.pneumacare.shared.web.ApiResponseBase;
import wfederico.pneumacare.shared.web.dto.LoginRequest;
import wfederico.pneumacare.shared.web.dto.LoginResponse;
import wfederico.pneumacare.shared.web.dto.RegisterRequest;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Authentication endpoints. Issues the JWT only as an HttpOnly cookie plus a
 * readable XSRF-TOKEN cookie; the body returns non-sensitive profile data only.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    /** Roles a user may self-assign at registration; privileged roles are admin-provisioned. */
    private static final Set<Role> SELF_REGISTERABLE_ROLES =
            EnumSet.of(Role.ROLE_THERAPIST, Role.ROLE_CHIEF_OF_GUARD);

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(AuthenticationManager authenticationManager,
                         JwtService jwtService,
                         JwtProperties jwtProperties,
                         UserRepository userRepository,
                         PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
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

        return authenticatedResponse(principal, "Autenticación exitosa");
    }

    @PreAuthorize("permitAll()")
    @PostMapping("/register")
    public ResponseEntity<ApiResponseBase<LoginResponse>> register(@Valid @RequestBody RegisterRequest request) {
        if (!SELF_REGISTERABLE_ROLES.contains(request.role())) {
            throw new BusinessLayerException("Rol no permitido para registro", HttpStatus.BAD_REQUEST);
        }
        if (userRepository.findByUsername(request.username()).isPresent()) {
            throw new BusinessLayerException("El nombre de usuario ya está en uso", HttpStatus.CONFLICT);
        }

        UserJpaEntity user = UserJpaEntity.builder()
                .username(request.username())
                .passwordHash(passwordEncoder.encode(request.password()))
                .displayName(request.displayName())
                .enabled(true)
                .roles(EnumSet.of(request.role()))
                .build();
        UserJpaEntity saved = userRepository.save(user);

        // Issue the session immediately so the SPA lands authenticated after sign-up.
        return authenticatedResponse(UserPrincipal.from(saved), "Registro exitoso");
    }

    private ResponseEntity<ApiResponseBase<LoginResponse>> authenticatedResponse(UserPrincipal principal, String message) {
        String token = jwtService.issueToken(principal);
        List<String> roles = principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        ResponseCookie jwtCookie = buildCookie(jwtProperties.getCookieName(), token, true, jwtProperties.getExpiration());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, jwtCookie.toString())
                .body(ApiResponseBase.<LoginResponse>builder()
                        .status(HttpStatus.OK.value())
                        .message(message)
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
