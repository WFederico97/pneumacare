package wfederico.pneumacare.inventory.infrastructure.persistence;

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
 * JPA entity for the {@code ventilator_models} table (V1 schema).
 *
 * <p>Does not extend {@code EntityBase}: the table has no audit columns and
 * this story does not add them (models are resolved-or-created as a side
 * effect of ventilator registration, never managed directly).
 *
 * <p>{@code brand} stays a {@code String} because the column is nullable and
 * predates the {@code VentilatorBrand} enum; rows created through the
 * inventory API always store {@code VentilatorBrand.name()}.
 */
@Entity
@Table(name = "ventilator_models")
@Getter
@Setter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class VentilatorModelJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "brand", length = 50)
    private String brand;

    @Column(name = "model", nullable = false, length = 100)
    private String model;

    @Column(name = "software_version", length = 50)
    private String softwareVersion;
}
