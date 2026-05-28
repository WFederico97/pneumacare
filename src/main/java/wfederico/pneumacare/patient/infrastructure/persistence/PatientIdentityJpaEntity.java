package wfederico.pneumacare.patient.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.*;
import wfederico.pneumacare.shared.security.encryption.AesAttributeConverter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA entity for the {@code patient_identities} table.
 *
 * <p>Contains all Personally Identifiable Information (PII) for a patient.
 * The fields {@link #firstName} and {@link #lastName} are
 * <strong>transparently encrypted at rest</strong> via {@link AesAttributeConverter}.
 * Hibernate stores Base64-encoded AES-256-GCM ciphertext in the database and
 * returns plain text to the application — callers see no difference.
 *
 * <h2>PII isolation</h2>
 * This entity is intentionally isolated from clinical data. All clinical tables
 * ({@code evaluations}, {@code airway_assessments}, etc.) reference
 * {@code patients.id}, never this table directly. This limits the blast radius
 * of a database breach and simplifies GDPR/Law-25.326 data-subject requests.
 *
 * <h2>Identifiers</h2>
 * Structured identifier values (DNI, CUIL, Passport, etc.) are stored in the
 * {@code patient_identifiers} table and linked via {@link #identifiers}.
 * Each entry is also PII-encrypted at rest via {@link AesAttributeConverter}.
 *
 * @see AesAttributeConverter
 */
@Entity
@Table(name = "patient_identities")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatientIdentityJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * [PII] First name — stored as AES-256-GCM encrypted Base64 in the database.
     * Plain text is returned transparently by {@link AesAttributeConverter}.
     */
    @Convert(converter = AesAttributeConverter.class)
    @Column(name = "first_name", nullable = false, columnDefinition = "TEXT")
    private String firstName;

    /**
     * [PII] Last name — stored as AES-256-GCM encrypted Base64 in the database.
     */
    @Convert(converter = AesAttributeConverter.class)
    @Column(name = "last_name", nullable = false, columnDefinition = "TEXT")
    private String lastName;

    /**
     * Structured identifiers (DNI, CUIL, Passport, etc.) belonging to this patient.
     * Each identifier value is PII-encrypted at rest via {@link AesAttributeConverter}.
     *
     * <p>Cascade {@code ALL} + {@code orphanRemoval} ensures identifiers are
     * persisted and deleted together with the owning identity record.
     */
    @Builder.Default
    @OneToMany(mappedBy = "patientIdentity", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PatientIdentifierJpaEntity> identifiers = new ArrayList<>();

    /**
     * [PII] Birth date — stored as a plain {@code DATE} column.
     * Not encrypted: an isolated date of birth has low re-identification risk
     * when the name is encrypted in the same table.
     */
    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;
}
