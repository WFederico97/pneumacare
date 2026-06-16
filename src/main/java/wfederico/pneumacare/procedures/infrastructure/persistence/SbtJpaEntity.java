package wfederico.pneumacare.procedures.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.*;
import wfederico.pneumacare.procedures.domain.ToleranceResult;
import wfederico.pneumacare.shared.data.EntityBase;

import java.util.UUID;

/**
 * JPA entity for the {@code spontaneous_breathing_trials} table (Flyway V1).
 *
 * <p>An append-only record of an SBT result: that a patient tolerated (or not)
 * breathing on their own, recorded within an OPEN shift. Rows are never mutated
 * after insert.
 *
 * <h2>Cross-context references</h2>
 * {@code patientId}, {@code shiftId} and {@code createdBy} are raw {@code UUID}
 * columns rather than {@code @ManyToOne} associations, because the Patient, Shift
 * and User aggregates live in other bounded contexts. This matches
 * {@code AirwayEventJpaEntity} and {@code MedicalShiftJpaEntity}.
 *
 * <h2>Mapped vs unmapped columns</h2>
 * The ticket models an SBT as a recorded result, not a time-tracked trial, so the
 * V1 columns {@code start_time} / {@code end_time} / {@code trial_mode} /
 * {@code failure_reason} are out of scope and intentionally not mapped (Flyway V12
 * drops the {@code start_time NOT NULL} constraint accordingly). The ticket's
 * {@code recorded_at} is the persistence timestamp, exposed from
 * {@link EntityBase#getCreatedAt()}; {@code performed_by} maps to {@code created_by};
 * {@code tolerance_result} maps to the {@code outcome} column.
 */
@Entity
@Table(name = "spontaneous_breathing_trials")
@Getter
@Setter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class SbtJpaEntity extends EntityBase {

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

    /** Trial duration in minutes; a positive integer (validated in the service). */
    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    /** Trial outcome. Stored as VARCHAR(20) in the {@code outcome} column. */
    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 20)
    private ToleranceResult toleranceResult;

    /**
     * Raw UUID FK to {@code users.id} (the ticket's {@code performed_by}). Derived
     * server-side from the authenticated principal — never accepted from the client.
     */
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
}
