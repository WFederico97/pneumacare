package wfederico.pneumacare.procedures.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.*;
import wfederico.pneumacare.procedures.domain.AirwayEventType;
import wfederico.pneumacare.shared.data.EntityBase;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JPA entity for the {@code airway_events} table (Flyway V1).
 *
 * <p>An append-only clinical event log: a row records that a patient's airway
 * changed (intubation / extubation / tracheostomy) at a given time, within an
 * OPEN shift. Rows are never mutated after insert.
 *
 * <h2>Cross-context references</h2>
 * {@code patientId}, {@code shiftId} and {@code createdBy} are raw {@code UUID}
 * columns rather than {@code @ManyToOne} associations, because the Patient, Shift
 * and User aggregates live in other bounded contexts. This matches
 * {@code MedicalShiftJpaEntity} and {@code EvaluationJpaEntity}.
 *
 * <h2>event_time vs created_at</h2>
 * {@code eventTime} is the clinically-reported timestamp supplied by the client
 * (the ticket's {@code event_timestamp}); {@code created_at} (from
 * {@link EntityBase}) is when the row was persisted. They are intentionally
 * distinct. Audit columns are added by Flyway V11.
 *
 * <p>The V1 table also has nullable {@code tube_size} / {@code is_successful} /
 * {@code complications_noted} columns; they are out of scope for PNMC-94 and are
 * intentionally not mapped (left null).
 */
@Entity
@Table(name = "airway_events")
@Getter
@Setter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class AirwayEventJpaEntity extends EntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Raw UUID FK to {@code patients.id} — entity owned by the patient context. */
    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    /**
     * Raw UUID FK to {@code medical_shifts.id}. Derived server-side from the
     * patient's currently OPEN shift — never accepted from the client.
     */
    @Column(name = "shift_id", nullable = false)
    private UUID shiftId;

    /** Clinically-reported event timestamp (ISO-8601), supplied by the client. */
    @Column(name = "event_time", nullable = false)
    private OffsetDateTime eventTime;

    /** Airway event type. Stored as VARCHAR(20). */
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 20)
    private AirwayEventType eventType;

    /**
     * Raw UUID FK to {@code users.id}. Derived server-side from the authenticated
     * principal — never accepted from the client.
     */
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
}
