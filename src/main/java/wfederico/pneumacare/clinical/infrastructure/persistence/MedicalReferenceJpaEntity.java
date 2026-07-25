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
 * JPA entity for the {@code medical_reference} knowledge-base table.
 *
 * <p>Each row is one curated guidance entry keyed by {@code (metric, band)},
 * where {@code band} is the name of an interpretation enum constant
 * ({@code RsbiInterpretation} / {@code PafiClassification} /
 * {@code CstatInterpretation}). Non-PII reference content.
 */
@Entity
@Table(name = "medical_reference")
@Getter
@Setter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class MedicalReferenceJpaEntity extends EntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Risk metric name: RSBI / PAFI / CSTAT (matches RiskMetric). */
    @Column(name = "metric", nullable = false, length = 20)
    private String metric;

    /** Interpretation enum constant name, e.g. UNFAVORABLE / MILD_ARDS / LOW. */
    @Column(name = "band", nullable = false, length = 50)
    private String band;

    /** Human-readable range descriptor, e.g. "> 105", "100-200 mmHg". */
    @Column(name = "range_descriptor", nullable = false, length = 100)
    private String rangeDescriptor;

    /** Clinical context label, e.g. "weaning readiness". */
    @Column(name = "context", nullable = false, length = 100)
    private String context;

    /** Curated guidance sentence. */
    @Column(name = "guidance_text", nullable = false, columnDefinition = "text")
    private String guidanceText;

    /** Cited manual/guideline. */
    @Column(name = "source_ref", nullable = false, length = 255)
    private String sourceRef;

    /** Severity rank; higher composes first. */
    @Column(name = "priority", nullable = false)
    private int priority;
}
