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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import wfederico.pneumacare.shared.exception.BusinessLayerException;
import wfederico.pneumacare.shared.security.user.UserJpaEntity;
import wfederico.pneumacare.shared.security.user.UserRepository;
import wfederico.pneumacare.shared.web.ApiResponseBase;
import wfederico.pneumacare.shared.web.dto.ChangePasswordRequest;
import wfederico.pneumacare.shared.web.dto.LoginRequest;
import wfederico.pneumacare.shared.web.dto.LoginResponse;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Authentication endpoints: login and logout only. Issues the JWT solely as an
 * HttpOnly cookie plus a readable XSRF-TOKEN cookie; the body returns
 * non-sensitive profile data only.
 *
 * <p>There is deliberately <strong>no self-registration</strong>. It previously
 * let an anonymous caller mint a THERAPIST or CHIEF_OF_GUARD account and receive
 * a session immediately, which handed decrypted patient PII to anyone who could
 * reach the app — defeating the Law 25.326 encryption applied at rest. Staff
 * accounts are provisioned by an administrator through {@code /api/v1/users},
 * which enforces the admin boundary.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

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

    /**
     * Changes the signed-in user's own password.
     *
     * <p>Required so a bootstrap or shared credential can actually be rotated:
     * previously only an administrator could change someone else's password, and
     * the sole administrator could not change their own.
     *
     * <p>A fresh cookie is issued so the caller stays signed in. Sessions already
     * open elsewhere keep working until their token expires — revoking those needs
     * token versioning, which is not implemented.
     */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/password")
    public ResponseEntity<ApiResponseBase<Void>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UUID userId = resolveUserId(authentication);

        UserJpaEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessLayerException("No autenticado", HttpStatus.UNAUTHORIZED));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessLayerException("La contraseña actual es incorrecta", HttpStatus.FORBIDDEN);
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BusinessLayerException(
                    "La nueva contraseña debe ser distinta de la actual", HttpStatus.BAD_REQUEST);
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        // Re-issue the session so the caller is not silently logged out.
        String token = jwtService.issueToken(UserPrincipal.from(user));
        ResponseCookie jwtCookie =
                buildCookie(jwtProperties.getCookieName(), token, true, jwtProperties.getExpiration());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, jwtCookie.toString())
                .body(ApiResponseBase.<Void>builder()
                        .status(HttpStatus.OK.value())
                        .message("Contraseña actualizada")
                        .traceId(MDC.get("traceId"))
                        .build());
    }

    /** The authenticated user's UUID, from the JWT {@code sub} claim. */
    private UUID resolveUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BusinessLayerException("No autenticado", HttpStatus.UNAUTHORIZED);
        }
        Object principal = authentication.getPrincipal();
        String subject = principal instanceof Jwt jwt ? jwt.getSubject() : authentication.getName();
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException ex) {
            throw new BusinessLayerException("No autenticado", HttpStatus.UNAUTHORIZED);
        }
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
