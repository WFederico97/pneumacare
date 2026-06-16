package wfederico.pneumacare.shift.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.*;
import wfederico.pneumacare.shared.data.EntityBase;

import java.util.UUID;

/**
 * JPA entity for the {@code shift_handovers} table (Flyway V1).
 *
 * <p>An append-only handover note: free-text clinical context submitted against an
 * OPEN shift so the incoming team has a summary. Notes are immutable once created
 * and a shift may have many of them (PNMC-92).
 *
 * <h2>Cross-context references</h2>
 * {@code shiftId} and {@code authorId} are raw {@code UUID} columns rather than
 * {@code @ManyToOne} associations, consistent with the rest of the shift context.
 *
 * <h2>Schema reconciliation</h2>
 * The V1 table was designed around a single structured handover per shift
 * ({@code incoming_notes} / {@code outgoing_notes} / {@code critical_events_summary} /
 * {@code closed_at}, with a {@code UNIQUE(shift_id)} constraint). PNMC-92 models a
 * simple immutable note instead, so Flyway V13 drops that unique constraint and adds
 * {@code author_id} / {@code notes_content} / audit columns; the legacy columns are
 * out of scope and intentionally not mapped. The ticket's {@code created_at} is the
 * persistence timestamp from {@link EntityBase}.
 */
@Entity
@Table(name = "shift_handovers")
@Getter
@Setter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class ShiftHandoverJpaEntity extends EntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Raw UUID FK to {@code medical_shifts.id}. From the path, must be an OPEN shift. */
    @Column(name = "shift_id", nullable = false)
    private UUID shiftId;

    /**
     * Raw UUID FK to {@code users.id}. Derived server-side from the authenticated
     * principal — never accepted from the client.
     */
    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    /** Free-text note content (non-empty, max 4000 chars; validated in the service). */
    @Column(name = "notes_content", nullable = false, columnDefinition = "TEXT")
    private String notesContent;
}
