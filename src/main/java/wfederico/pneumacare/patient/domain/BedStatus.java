package wfederico.pneumacare.patient.domain;

/**
 * Availability status of an ICU bed ({@code icu_beds.status}).
 *
 * <p>Stored as {@code VARCHAR(50)} via {@code @Enumerated(EnumType.STRING)}
 * to match the Flyway V1 column definition {@code DEFAULT 'AVAILABLE'}.
 *
 * <ul>
 *   <li>{@link #AVAILABLE} — the bed is empty and can accept a new admission.</li>
 *   <li>{@link #OCCUPIED}  — a patient is currently assigned to this bed.</li>
 *   <li>{@link #MAINTENANCE} — the bed is temporarily out of service.</li>
 * </ul>
 */
public enum BedStatus {
    AVAILABLE,
    OCCUPIED,
    MAINTENANCE
}
