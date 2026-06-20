package wfederico.pneumacare.shift.infrastructure.persistence.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.RevisionEntity;
import org.hibernate.envers.RevisionNumber;
import org.hibernate.envers.RevisionTimestamp;

import java.util.UUID;

/**
 * Custom Envers revision entity ({@code revinfo} table) for PNMC-134.
 *
 * <p>Replaces Envers' built-in {@code DefaultRevisionEntity} so that each revision
 * also records the acting user ({@link #actorId}). It is the single, persistence-unit
 * wide revision entity; only the {@code shift} context's entities are currently
 * {@link org.hibernate.envers.Audited @Audited}, so every revision belongs to a shift
 * or handover write.
 *
 * <p>{@link #actorId} is populated by {@link ShiftRevisionListener} from the Spring
 * Security context at flush time, so every create/update is captured with actor +
 * timestamp. The Envers schema is created by Flyway V14 in staging/prod and by Hibernate
 * ({@code ddl=update}) in dev.
 */
@Entity
@Table(name = "revinfo")
@RevisionEntity(ShiftRevisionListener.class)
@Getter
@Setter
public class ShiftRevisionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "revinfo_seq_gen")
    @SequenceGenerator(name = "revinfo_seq_gen", sequenceName = "revinfo_seq", allocationSize = 1)
    @RevisionNumber
    @Column(name = "rev")
    private int rev;

    @RevisionTimestamp
    @Column(name = "revtstmp")
    private long revtstmp;

    /**
     * UUID of the user who triggered the revision, resolved from the JWT {@code sub}
     * claim via {@link wfederico.pneumacare.shared.security.AuthenticatedActor}.
     * The nil UUID when no parseable principal is present (e.g. the dev profile).
     */
    @Column(name = "actor_id")
    private UUID actorId;
}
