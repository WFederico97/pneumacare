package wfederico.pneumacare.patient.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.*;
import wfederico.pneumacare.shared.security.encryption.AesAttributeConverter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * JPA entity for the {@code patient_identities} table.
 *
 * <p>Contains all Personally Identifiable Information (PII) for a patient.
 * The fields {@link #firstName}, {@link #lastName}, and {@link #nationalId} are
 * <strong>transparently encrypted at rest</strong> via {@link AesAttributeConverter}.
 * Hibernate stores Base64-encoded AES-256-GCM ciphertext in the database and
 * returns plain text to the application — callers see no difference.
 *
 * <h3>PII isolation</h3>
 * This entity is intentionally isolated from clinical data. All clinical tables
 * ({@code evaluations}, {@code airway_assessments}, etc.) reference
 * {@code patients.id}, never this table directly. This limits the blast radius
 * of a database breach and simplifies GDPR/Law-25.326 data-subject requests.
 *
 * <h3>UNIQUE constraint on nationalId</h3>
 * AES-GCM uses a random IV per write, so the same DNI/CUIL encrypts to a
 * different ciphertext on every INSERT. The DB-level {@code UNIQUE} constraint
 * was dropped in {@code V2__encrypt_patient_identity_columns.sql}.
 * Uniqueness is enforced at the application layer (service query).
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
     * [PII] National ID (DNI / CUIL) — stored as AES-256-GCM encrypted Base64.
     *
     * <p>See class-level Javadoc for why the DB-level UNIQUE constraint was removed.
     */
    @Convert(converter = AesAttributeConverter.class)
    @Column(name = "national_id", nullable = false, columnDefinition = "TEXT")
    private String nationalId;

    /**
     * [PII] Birth date — stored as a plain {@code DATE} column.
     * Not encrypted: an isolated date of birth has low re-identification risk
     * when the name and national ID are encrypted in the same table.
     */
    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;
}
