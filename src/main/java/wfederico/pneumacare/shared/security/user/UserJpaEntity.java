package wfederico.pneumacare.shared.security.user;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import wfederico.pneumacare.shared.data.EntityBase;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * JPA entity for the {@code users} table — the persisted identity record.
 *
 * <p>Roles are held as an {@link ElementCollection} of {@link Role} stored as
 * {@code VARCHAR} into {@code user_roles.role}; the database CHECK constraint and
 * the enum agree on the canonical values. Extends {@link EntityBase} for the
 * {@code created_at}/{@code updated_at} audit columns.
 *
 * <p>Stores password hashes only (BCrypt) plus username and display name — no
 * other PII.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class UserJpaEntity extends EntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "username", nullable = false, unique = true, length = 100)
    private String username;

    /** BCrypt hash — never plaintext. */
    @Column(name = "password_hash", nullable = false, length = 255)
    @ToString.Exclude
    private String passwordHash;

    @Column(name = "display_name", length = 150)
    private String displayName;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role", nullable = false, length = 40)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Set<Role> roles = EnumSet.noneOf(Role.class);
}
