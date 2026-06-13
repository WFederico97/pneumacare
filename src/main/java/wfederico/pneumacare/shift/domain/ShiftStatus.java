package wfederico.pneumacare.shift.domain;

/**
 * Lifecycle status of a medical shift ({@code medical_shifts.status}).
 *
 * <p>Stored as {@code VARCHAR(20)} via {@code @Enumerated(EnumType.STRING)}
 * to match the Flyway V1 column definition {@code DEFAULT 'OPEN'}.
 *
 * <ul>
 *   <li>{@link #OPEN}   — the shift is active; clinical evaluations may be tied to it.</li>
 *   <li>{@link #CLOSED} — the shift has ended; {@code end_time} is populated.</li>
 * </ul>
 *
 * <p>"Active" is not a separate state — it is simply a shift whose status is {@code OPEN}.
 */
public enum ShiftStatus {
    OPEN,
    CLOSED
}
