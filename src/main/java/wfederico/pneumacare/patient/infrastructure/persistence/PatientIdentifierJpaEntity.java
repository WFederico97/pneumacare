package wfederico.pneumacare.patient.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.*;
import wfederico.pneumacare.shared.security.encryption.AesAttributeConverter;

/**
 * JPA entity for the {@code patient_identifiers} table.
 *
 * <p>Stores a single identifier value (e.g. "12.345.678") associated with
 * a {@link PatientIdentityJpaEntity} and classified by a
 * {@link PatientIdentifierTypeJpaEntity} (DNI, CUIL, Passport, etc.).
 * One patient identity may hold multiple identifiers of different types.
 *
 * <h2>PII</h2>
 * {@link #patientIdentifierName} is the raw identifier value and is considered
 * Personally Identifiable Information under Argentine Law 25.326.
 * It is <strong>transparently encrypted at rest</strong> via
 * {@link AesAttributeConverter} (AES-256-GCM, random IV per write).
 * The identifier <em>type</em> name is not PII — it is a catalog value
 * (e.g. "DNI") and is stored as plain text.
 *
 * @see PatientIdentityJpaEntity
 * @see PatientIdentifierTypeJpaEntity
 * @see AesAttributeConverter
 */
@Entity
@Table(name = "patient_identifiers")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientIdentifierJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "patient_identifier_id", updatable = false, nullable = false)
    private int patientIdentifierId;

    /**
     * [PII] The raw identifier value (e.g. "12.345.678" for a DNI, "20-12345678-9" for a CUIL).
     * Stored as AES-256-GCM encrypted Base64 in the database.
     * Plain text is returned transparently by {@link AesAttributeConverter}.
     */
    @Convert(converter = AesAttributeConverter.class)
    @Column(name = "patient_identifier_name", nullable = false, columnDefinition = "TEXT")
    private String patientIdentifierName;

    /**
     * The patient identity this identifier belongs to.
     * Foreign key: {@code patient_identity_id} → {@code patient_identities.id}.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_identity_id", nullable = false)
    private PatientIdentityJpaEntity patientIdentity;

    /**
     * The type that classifies this identifier (DNI, CUIL, Passport, etc.).
     * Foreign key: {@code patient_identifier_type_id} → {@code patient_identifier_types.patient_identifier_type_id}.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_identifier_type_id", nullable = false)
    private PatientIdentifierTypeJpaEntity patientIdentifierType;
}
