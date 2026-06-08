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
import wfederico.pneumacare.patient.domain.BedStatus;

import java.util.UUID;

/**
 * JPA entity for the {@code icu_beds} table.
 *
 * <p>Represents a physical bed within an Intensive Care Unit.
 * The {@code status} field uses {@link BedStatus} stored as {@code VARCHAR}
 * ({@link EnumType#STRING}) to match the {@code DEFAULT 'AVAILABLE'} constraint
 * declared in the Flyway schema.
 *
 * <h2>Concurrency note</h2>
 * Simultaneous admission requests on the same bed could both read {@code AVAILABLE}
 * before either write flushes. For the thesis MVP this is accepted as a known
 * limitation. Production hardening would add either {@code @Version} (optimistic
 * locking) or a {@code SELECT … FOR UPDATE} query (pessimistic locking).
 *
 * @see BedStatus
 * @see PatientJpaEntity
 */
@Entity
@Table(name = "icu_beds")
@Getter
@Setter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class IcuBedJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * The ICU this bed belongs to.
     * {@code FetchType.LAZY} — only loaded when explicitly accessed.
     * Excluded from {@code toString()} to avoid lazy-proxy logging outside a transaction.
     */
    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "icu_id", nullable = false)
    private IcuJpaEntity icu;

    /**
     * Physical bed identifier within the ICU (e.g. "BED-001", "Cama 3").
     */
    @Column(name = "bed_number", nullable = false, length = 50)
    private String bedNumber;

    /**
     * Current availability status of this bed.
     * Stored as {@code VARCHAR(50)} matching Flyway V1 {@code DEFAULT 'AVAILABLE'}.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private BedStatus status = BedStatus.AVAILABLE;
}
