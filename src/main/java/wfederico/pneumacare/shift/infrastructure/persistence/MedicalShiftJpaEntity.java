package wfederico.pneumacare.shift.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;
import wfederico.pneumacare.shared.data.EntityBase;
import wfederico.pneumacare.shift.domain.ShiftStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JPA entity for the {@code medical_shifts} table (Flyway V1).
 *
 * <p>Represents a Chief-of-Guard duty period for one ICU. At most one {@code OPEN}
 * shift may exist per {@code icu_id} — enforced at the DB level by a partial unique
 * index (Flyway V9) so the rule holds under concurrent requests.
 *
 * <h2>Cross-context references</h2>
 * {@code icuId} and {@code chiefUserId} are stored as raw {@code UUID} columns rather
 * than {@code @ManyToOne} associations, because the ICU and User aggregates live in
 * other bounded contexts. This matches {@code EvaluationJpaEntity}.
 *
 * <h2>Auditing</h2>
 * Extends {@link EntityBase} for {@code created_at}/{@code updated_at} (row-level
 * timestamps; audit columns added by Flyway V8). In addition, the entity is
 * {@link Audited} (PNMC-134): Hibernate Envers records a full revision history in
 * {@code medical_shifts_aud} so retroactive edits to {@code CLOSED} shifts can be
 * detected and queried. Each revision captures the acting user and a timestamp via
 * the custom revision entity. The Envers schema is created by Flyway V14.
 */
@Entity
@Audited
@Table(name = "medical_shifts")
@Getter
@Setter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class MedicalShiftJpaEntity extends EntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Raw UUID FK to {@code intensive_care_units.id} — entity owned by the patient context. */
    @Column(name = "icu_id", nullable = false)
    private UUID icuId;

    /**
     * Raw UUID FK to {@code users.id}. Derived server-side from the authenticated
     * principal — never accepted from the client.
     */
    @Column(name = "chief_user_id", nullable = false)
    private UUID chiefUserId;

    /** Server-clock UTC timestamp set when the shift is opened. */
    @Column(name = "start_time", nullable = false)
    private OffsetDateTime startTime;

    /** Server-clock UTC timestamp set when the shift is closed; null while OPEN. */
    @Column(name = "end_time")
    private OffsetDateTime endTime;

    /** Current lifecycle status. Stored as VARCHAR(20) matching V1 {@code DEFAULT 'OPEN'}. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    @Builder.Default
    private ShiftStatus status = ShiftStatus.OPEN;

}
