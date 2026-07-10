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
 * JPA entity for the {@code clinical_combination_rule} knowledge-base table.
 *
 * <p>Each row is one piece of cross-metric guidance that applies only when
 * several interpretation bands co-occur — synthesis a single-metric
 * {@code medical_reference} row cannot express (e.g. a favorable RSBI alongside
 * ARDS-range oxygenation).
 *
 * <p>Each {@code *Band} column is a <strong>wildcard when {@code null}</strong>;
 * otherwise it holds a comma-separated allow-list of interpretation enum
 * constant names. A rule matches an evaluation only when every non-null band
 * column contains that evaluation's corresponding band. Non-PII reference
 * content.
 */
@Entity
@Table(name = "clinical_combination_rule")
@Getter
@Setter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class ClinicalCombinationRuleJpaEntity extends EntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Stable identifier for the rule, e.g. {@code oxygenation-gates-weaning}. */
    @Column(name = "rule_name", nullable = false, length = 80)
    private String ruleName;

    /** Allowed RSBI bands (comma-separated) or {@code null} for any. */
    @Column(name = "rsbi_band", length = 60)
    private String rsbiBand;

    /** Allowed PaFi bands (comma-separated) or {@code null} for any. */
    @Column(name = "pafi_band", length = 60)
    private String pafiBand;

    /** Allowed Cstat bands (comma-separated) or {@code null} for any. */
    @Column(name = "cstat_band", length = 60)
    private String cstatBand;

    /** Allowed driving-pressure bands (comma-separated) or {@code null} for any. */
    @Column(name = "dp_band", length = 30)
    private String dpBand;

    /** Curated cross-metric guidance sentence. */
    @Column(name = "guidance_text", nullable = false, columnDefinition = "text")
    private String guidanceText;

    /** Cited guideline(s); may hold several refs joined by {@code "; "}. */
    @Column(name = "source_ref", nullable = false, length = 500)
    private String sourceRef;

    /** Composition rank; higher composes first. */
    @Column(name = "priority", nullable = false)
    private int priority;
}
