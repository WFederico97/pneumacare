package wfederico.pneumacare.patient.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.UUID;

/**
 * JPA entity for the {@code intensive_care_units} table.
 *
 * <p>Represents an Intensive Care Unit within a hospital. No PII is stored here.
 * The {@code hospitalId} is kept as a raw UUID column rather than a full
 * {@code @ManyToOne} association because the hospital/province hierarchy belongs
 * to a separate bounded context that has not yet been modelled as JPA entities.
 * Keeping a raw FK avoids pulling unrelated aggregate roots into the patient context.
 *
 * <h2>Usage in admission flow</h2>
 * An ICU is required when admitting a patient ({@code patients.icu_id}). The service
 * validates that the ICU exists before persisting the patient row.
 */
@Entity
@Table(name = "intensive_care_units")
@Getter
@Setter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class IcuJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Raw UUID foreign key to the {@code hospitals} table.
     * Not navigated as a JPA association — the hospital entity is out of scope
     * for this bounded context.
     */
    @Column(name = "hospital_id", nullable = false)
    private UUID hospitalId;

    /**
     * Human-readable ICU name (e.g. "UTI Central", "UCI Neonatología").
     */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /**
     * Short unique code identifying the ICU within the hospital (e.g. "UTI-01").
     */
    @Column(name = "code", nullable = false, length = 20, unique = true)
    private String code;
}
