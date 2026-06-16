package wfederico.pneumacare.patient.domain;


/**
 * Airway / respiratory state of an admitted patient
 * ({@code patients.respiratory_status}).
 *
 * <p>Distinct from {@link ClinicalStatus}, which tracks the admission lifecycle
 * (ADMITTED / DISCHARGED / TRANSFERRED). This enum tracks the patient's airway
 * and is driven by airway events (see the {@code procedures} context).
 *
 * <p>Stored as {@code VARCHAR(50)} via {@code @Enumerated(EnumType.STRING)};
 * Flyway adds the column with {@code DEFAULT 'SPONTANEOUS'}.
 *
 * <ul>
 *   <li>{@link #SPONTANEOUS}  — breathing on their own (no artificial airway).</li>
 *   <li>{@link #INTUBATED}    — endotracheal tube in place.</li>
 *   <li>{@link #TRACHEOSTOMY} — tracheostomy in place.</li>
 * </ul>
 */
public enum RespiratoryStatus {
    SPONTANEOUS,
    INTUBATED,
    TRACHEOSTOMY
}
