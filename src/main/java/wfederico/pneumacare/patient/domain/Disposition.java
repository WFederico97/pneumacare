package wfederico.pneumacare.patient.domain;

/**
 * Clinical disposition of a closed ICU episode ({@code patients.disposition}).
 *
 * <p>Stored as {@code VARCHAR(50)} via {@code @Enumerated(EnumType.STRING)};
 * Flyway V29 adds the column with a CHECK over these values. Null on open
 * episodes — the {@code chk_patients_terminus} constraint pairs it with
 * {@code discharge_date}.
 *
 * <p>{@link #DECEASED} and {@link #WITHDRAWAL_OF_CARE} are deliberately
 * separate: clinically and ethically distinct, and conflating them distorts
 * risk-adjusted mortality reporting.
 */
public enum Disposition {
    /** Discharged home. */
    HOME,
    /** Step-down to a general ward within this hospital. */
    WARD,
    /** Transferred to another facility. */
    TRANSFER_EXTERNAL,
    /** Died in this ICU. */
    DECEASED,
    /** Death after planned withdrawal of life-sustaining treatment. */
    WITHDRAWAL_OF_CARE;

    /**
     * Coarse lifecycle status implied by this disposition. Deceased patients
     * are {@code DISCHARGED} in lifecycle terms — the episode ended in this
     * ICU; the disposition carries the clinical truth.
     */
    public ClinicalStatus toClinicalStatus() {
        return this == TRANSFER_EXTERNAL ? ClinicalStatus.TRANSFERRED : ClinicalStatus.DISCHARGED;
    }
}
