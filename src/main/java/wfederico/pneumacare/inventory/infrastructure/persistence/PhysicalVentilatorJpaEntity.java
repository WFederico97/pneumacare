package wfederico.pneumacare.inventory.infrastructure.persistence;

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
import wfederico.pneumacare.inventory.domain.VentilatorStatus;
import wfederico.pneumacare.shared.data.EntityBase;

import java.util.UUID;

/**
 * JPA entity for the {@code physical_ventilators} table (V1 schema + V17
 * audit columns).
 *
 * <p>{@code icu_id} is a raw UUID FK — the ICU entity belongs to the patient
 * context and bounded contexts do not share JPA entities. Existence is
 * validated via {@link PhysicalVentilatorRepository#icuExists(UUID)}.
 */
@Entity
@Table(name = "physical_ventilators")
@Getter
@Setter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class PhysicalVentilatorJpaEntity extends EntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "icu_id", nullable = false)
    private UUID icuId;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "model_id", nullable = false)
    private VentilatorModelJpaEntity model;

    @Column(name = "serial_number", nullable = false, length = 100, unique = true)
    private String serialNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private VentilatorStatus status = VentilatorStatus.AVAILABLE;
}
