package wfederico.pneumacare.patient.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import wfederico.pneumacare.patient.domain.ClinicalStatus;
import wfederico.pneumacare.patient.domain.Disposition;
import wfederico.pneumacare.patient.domain.RespiratoryStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JPA entity for the {@code patients} table — the operational patient record.
 *
 * <p>This entity contains <strong>no PII</strong>. All personally identifiable
 * information (name, date of birth, identifier values) lives in the
 * {@code patient_identities} / {@code patient_identifiers} tables and is linked
 * here only via the {@code identity_id} foreign key. This separation limits the
 * blast radius of a database breach and simplifies GDPR / Law-25.326
 * data-subject requests.
 *
 * <h2>Relationship to PatientIdentityJpaEntity</h2>
 * Since Flyway V29 the {@code patients} row is one ICU <em>episode</em>: a
 * person ({@code patient_identities}) may have many episodes over time
 * (readmission), with at most one open — enforced by the partial unique index
 * {@code uq_patients_open_episode}. The association is therefore a
 * {@code @ManyToOne}; only the operational side holds the FK column
 * ({@code identity_id}) and the PII side is unaware of this association.
 *
 * <h2>Bed assignment</h2>
 * {@code bed} is nullable per the V1 schema ({@code bed_id UUID}) — a patient
 * may be admitted without a bed (e.g. during overflow) and assigned one later.
 *
 * <h2>Admission date</h2>
 * {@code admissionDate} is populated by {@link CreationTimestamp} at
 * {@code INSERT} time, matching the Flyway {@code DEFAULT now()} behaviour.
 *
 * @see PatientIdentityJpaEntity
 * @see IcuBedJpaEntity
 * @see ClinicalStatus
 */
@Entity
@Table(name = "patients")
@Getter
@Setter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class PatientJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * The ICU in which this patient is admitted.
     * Excluded from {@code toString()} to prevent lazy-proxy resolution in logs.
     */
    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "icu_id", nullable = false)
    private IcuJpaEntity icu;

    /**
     * Link to the PII identity record. {@code @ManyToOne} since V29: a person
     * may have multiple episodes (readmission); at most one open, enforced by
     * the partial unique index {@code uq_patients_open_episode}.
     * Excluded from {@code toString()} to prevent lazy-proxy resolution in logs.
     */
    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "identity_id", nullable = false)
    private PatientIdentityJpaEntity identity;

    /**
     * The bed currently assigned to this patient. Nullable — a patient may be
     * admitted before a bed is available, consistent with the V1 schema.
     * Excluded from {@code toString()} to prevent lazy-proxy resolution in logs.
     */
    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bed_id")
    private IcuBedJpaEntity bed;

    /**
     * Clinical lifecycle status of this patient admission.
     * Stored as {@code VARCHAR(50)} to match Flyway V1 {@code DEFAULT 'ADMITTED'}.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "clinical_status", nullable = false, length = 50)
    @Builder.Default
    private ClinicalStatus clinicalStatus = ClinicalStatus.ADMITTED;

    /**
     * Airway / respiratory state of this patient, driven by airway events
     * (see the {@code procedures} context). Distinct from {@link #clinicalStatus}.
     * Stored as {@code VARCHAR(50)}; Flyway V10 adds the column with
     * {@code DEFAULT 'SPONTANEOUS'}.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "respiratory_status", nullable = false, length = 50)
    @Builder.Default
    private RespiratoryStatus respiratoryStatus = RespiratoryStatus.SPONTANEOUS;

    /**
     * Timestamp at which the patient was admitted. Set once at INSERT by
     * {@link CreationTimestamp} — never updated, matching Flyway {@code DEFAULT now()}.
     */
    @CreationTimestamp
    @Column(name = "admission_date", nullable = false, updatable = false)
    private OffsetDateTime admissionDate;

    /**
     * Timestamp at which this episode closed. Null while the episode is open;
     * paired with {@link #disposition} by the {@code chk_patients_terminus}
     * DB constraint (Flyway V29).
     */
    @Column(name = "discharge_date")
    private OffsetDateTime dischargeDate;

    /**
     * Clinical disposition of the closed episode. Null while open — see
     * {@link #dischargeDate}. Stored as {@code VARCHAR(50)} per Flyway V29.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "disposition", length = 50)
    private Disposition disposition;
}
