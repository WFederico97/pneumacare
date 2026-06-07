package wfederico.pneumacare.patient.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;

/**
 * JPA entity for the {@code patient_identifier_types} table.
 *
 * <p>Acts as a catalog / lookup table for identifier types (e.g. DNI, CUIL,
 * Passport). Type names are <em>not</em> PII — they are generic labels, not
 * personal data. Only the identifier <em>value</em> stored in
 * {@link PatientIdentifierJpaEntity#getPatientIdentifierName()} is PII.
 *
 * @see PatientIdentifierJpaEntity
 */
@Entity
@Table(name = "patient_identifier_types")
@Getter
@Setter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class PatientIdentifierTypeJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "patient_identifier_type_id", updatable = false, nullable = false)
    private Integer patientIdentifierTypeId;

    @Column(name = "patient_identifier_type_name", nullable = false, unique = true)
    private String patientIdentifierTypeName;

    @Column(name = "patient_identifier_type_description")
    private String patientIdentifierTypeDescription;

    /**
     * Inverse side of the catalog → data relationship. Read-only: the catalog
     * must never cascade mutations into identifier (PII) rows.
     * Excluded from {@code toString()} to avoid triggering lazy loads in logs.
     */
    @ToString.Exclude
    @OneToMany(mappedBy = "patientIdentifierType")
    private List<PatientIdentifierJpaEntity> patientIdentifiers;
}
