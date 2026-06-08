package wfederico.pneumacare.patient.domain;

/**
 * Lifecycle status of an admitted patient ({@code patients.clinical_status}).
 *
 * <p>Stored as {@code VARCHAR(50)} via {@code @Enumerated(EnumType.STRING)}
 * to match the Flyway V1 column definition {@code DEFAULT 'ADMITTED'}.
 *
 * <ul>
 *   <li>{@link #ADMITTED}    — patient is currently in the ICU.</li>
 *   <li>{@link #DISCHARGED}  — patient has been discharged.</li>
 *   <li>{@link #TRANSFERRED} — patient was transferred to another unit or facility.</li>
 * </ul>
 */
public enum ClinicalStatus {
    ADMITTED,
    DISCHARGED,
    TRANSFERRED
}
