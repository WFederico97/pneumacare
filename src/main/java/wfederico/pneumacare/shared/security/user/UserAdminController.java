package wfederico.pneumacare.shared.security.user;

import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import wfederico.pneumacare.shared.web.ApiResponseBase;
import wfederico.pneumacare.shared.web.dto.CreateUserRequest;
import wfederico.pneumacare.shared.web.dto.UpdateUserRequest;
import wfederico.pneumacare.shared.web.dto.UserResponse;

import java.util.List;
import java.util.UUID;

/**
 * Administrative user CRUD. Authorized for chiefs and admins
 * ({@code ROLE_ADMIN} inherits {@code ROLE_CHIEF_OF_GUARD} via the role
 * hierarchy). "Delete" is a soft disable; escalation rules live in
 * {@link UserAdminService}.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserAdminController {

    private final UserAdminService userAdminService;

    public UserAdminController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @GetMapping
    @PreAuthorize("hasRole('CHIEF_OF_GUARD')")
    public ApiResponseBase<List<UserResponse>> list() {
        return ok(userAdminService.list(), "Usuarios obtenidos");
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('CHIEF_OF_GUARD')")
    public ApiResponseBase<UserResponse> get(@PathVariable UUID id) {
        return ok(userAdminService.get(id), "Usuario obtenido");
    }

    @PostMapping
    @PreAuthorize("hasRole('CHIEF_OF_GUARD')")
    public ResponseEntity<ApiResponseBase<UserResponse>> create(@Valid @RequestBody CreateUserRequest request,
                                                                Authentication authentication) {
        UserResponse created = userAdminService.create(request, isAdmin(authentication));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponseBase.<UserResponse>builder()
                        .status(HttpStatus.CREATED.value())
                        .message("Usuario creado")
                        .data(created)
                        .traceId(MDC.get("traceId"))
                        .build());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('CHIEF_OF_GUARD')")
    public ApiResponseBase<UserResponse> update(@PathVariable UUID id,
                                                @Valid @RequestBody UpdateUserRequest request,
                                                Authentication authentication) {
        return ok(userAdminService.update(id, request, isAdmin(authentication)), "Usuario actualizado");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('CHIEF_OF_GUARD')")
    public ApiResponseBase<Void> disable(@PathVariable UUID id, Authentication authentication) {
        userAdminService.disable(id, callerId(authentication), isAdmin(authentication));
        return ok(null, "Usuario deshabilitado");
    }

    private static boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
    }

    private static UUID callerId(Authentication authentication) {
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static <T> ApiResponseBase<T> ok(T data, String message) {
        return ApiResponseBase.<T>builder()
                .status(HttpStatus.OK.value())
                .message(message)
                .data(data)
                .traceId(MDC.get("traceId"))
                .build();
    }
}
