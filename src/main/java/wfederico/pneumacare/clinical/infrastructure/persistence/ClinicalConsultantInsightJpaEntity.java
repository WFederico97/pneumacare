package wfederico.pneumacare.clinical.infrastructure.persistence;

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
import wfederico.pneumacare.shared.data.EntityBase;

import java.util.UUID;

/**
 * JPA entity for the {@code clinical_consultant_insights} cache table.
 *
 * <p>One row per evaluation ({@code evaluation_id} is unique). {@code insightText}
 * is the composed, reference-grounded guidance string produced by the DB-backed
 * consultant. Derived solely from curated references — never carries PII.
 */
@Entity
@Table(name = "clinical_consultant_insights")
@Getter
@Setter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class ClinicalConsultantInsightJpaEntity extends EntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "evaluation_id", nullable = false, unique = true)
    private UUID evaluationId;

    // columnDefinition = text so Hibernate's dev ddl matches the V20 TEXT column;
    // cross-metric guidance routinely exceeds the default varchar(255).
    @Column(name = "insight_text", nullable = false, columnDefinition = "text")
    private String insightText;
}
