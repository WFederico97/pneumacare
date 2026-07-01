package wfederico.pneumacare.shared.security.user;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import wfederico.pneumacare.shared.exception.BusinessLayerException;
import wfederico.pneumacare.shared.web.dto.CreateUserRequest;
import wfederico.pneumacare.shared.web.dto.UpdateUserRequest;
import wfederico.pneumacare.shared.web.dto.UserResponse;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Administrative CRUD over the {@code users} table (IAM management).
 *
 * <p>Authorized at the controller for chiefs and admins. Two invariants are
 * enforced here regardless of the caller's request:
 * <ul>
 *   <li>only an admin may grant {@code ROLE_ADMIN} or modify an existing admin
 *       account (no privilege escalation by a chief);</li>
 *   <li>"delete" is a <em>soft</em> disable — the row is kept so the forensic
 *       audit trail and foreign-key references survive; you cannot disable your
 *       own account.</li>
 * </ul>
 */
@Service
public class UserAdminService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserAdminService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return userRepository.findAll().stream()
                .sorted(Comparator.comparing(UserJpaEntity::getUsername, String.CASE_INSENSITIVE_ORDER))
                .map(UserAdminService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID id) {
        return toResponse(require(id));
    }

    @Transactional
    public UserResponse create(CreateUserRequest request, boolean callerIsAdmin) {
        guardAdminRoleAssignment(request.roles(), callerIsAdmin);
        if (userRepository.findByUsername(request.username()).isPresent()) {
            throw new BusinessLayerException("El nombre de usuario ya está en uso", HttpStatus.CONFLICT);
        }

        UserJpaEntity user = UserJpaEntity.builder()
                .username(request.username())
                .passwordHash(passwordEncoder.encode(request.password()))
                .displayName(request.displayName())
                .enabled(request.enabled() == null || request.enabled())
                .roles(java.util.EnumSet.copyOf(request.roles()))
                .build();
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse update(UUID id, UpdateUserRequest request, boolean callerIsAdmin) {
        UserJpaEntity user = require(id);
        guardAdminTarget(user, callerIsAdmin);
        guardAdminRoleAssignment(request.roles(), callerIsAdmin);

        user.setDisplayName(request.displayName());
        user.setRoles(java.util.EnumSet.copyOf(request.roles()));
        user.setEnabled(request.enabled());
        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        return toResponse(userRepository.save(user));
    }

    /** Soft delete: disable the account (cannot disable yourself). */
    @Transactional
    public void disable(UUID id, UUID callerId, boolean callerIsAdmin) {
        if (id.equals(callerId)) {
            throw new BusinessLayerException("No podés deshabilitar tu propia cuenta", HttpStatus.BAD_REQUEST);
        }
        UserJpaEntity user = require(id);
        guardAdminTarget(user, callerIsAdmin);
        user.setEnabled(false);
        userRepository.save(user);
    }

    private UserJpaEntity require(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessLayerException("Usuario no encontrado", HttpStatus.NOT_FOUND));
    }

    /** A chief may not grant ADMIN to anyone. */
    private void guardAdminRoleAssignment(Set<Role> roles, boolean callerIsAdmin) {
        if (roles.contains(Role.ROLE_ADMIN) && !callerIsAdmin) {
            throw new BusinessLayerException("Solo un administrador puede asignar el rol de administrador",
                    HttpStatus.FORBIDDEN);
        }
    }

    /** A chief may not modify or disable an existing admin account. */
    private void guardAdminTarget(UserJpaEntity target, boolean callerIsAdmin) {
        if (target.getRoles().contains(Role.ROLE_ADMIN) && !callerIsAdmin) {
            throw new BusinessLayerException("Solo un administrador puede modificar una cuenta de administrador",
                    HttpStatus.FORBIDDEN);
        }
    }

    private static UserResponse toResponse(UserJpaEntity user) {
        List<String> roles = user.getRoles().stream()
                .map(Role::name)
                .sorted()
                .toList();
        return new UserResponse(user.getId(), user.getUsername(), user.getDisplayName(), roles, user.isEnabled());
    }
}
