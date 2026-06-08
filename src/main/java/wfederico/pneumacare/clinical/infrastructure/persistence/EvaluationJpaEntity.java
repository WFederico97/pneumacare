package wfederico.pneumacare.clinical.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import wfederico.pneumacare.clinical.domain.CstatInterpretation;
import wfederico.pneumacare.clinical.domain.PafiClassification;
import wfederico.pneumacare.clinical.domain.RsbiInterpretation;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * JPA entity for the {@code evaluations} table.
 *
 * <p>Records a single snapshot of ventilator parameters for an admitted patient,
 * together with the three pre-computed clinical indices (RSBI, PaFi, Cstat).
 * The record is <strong>immutable after insertion</strong> — no business operation
 * updates it.
 *
 * <h2>Cross-context FK strategy</h2>
 * {@code patientId}, {@code shiftId}, {@code physicalVentilatorId}, and
 * {@code createdBy} are stored as raw UUID columns rather than {@code @ManyToOne}
 * associations. Those entities belong to other bounded contexts that are not yet
 * fully modelled as JPA entities; a raw UUID FK avoids pulling unrelated aggregate
 * roots into the clinical context. The FK constraints are enforced at the database
 * level via Flyway (staging/prod) or are absent in dev where Flyway is disabled.
 *
 * <h2>Extended parameters</h2>
 * {@code extendedParameters} is a free-form JSONB map that strategies or devices
 * may populate with brand-specific readings not covered by the fixed columns.
 * Hibernate serialises/deserialises the map transparently via
 * {@code @JdbcTypeCode(SqlTypes.JSON)}.
 */
@Entity
@Table(name = "evaluations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluationJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Raw UUID FK to {@code patients.id} — cross-bounded-context reference. */
    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    /** Raw UUID FK to {@code medical_shifts.id} — entity not yet modelled in this context. */
    @Column(name = "shift_id", nullable = false)
    private UUID shiftId;

    /** Raw UUID FK to {@code physical_ventilators.id} — entity not yet modelled. */
    @Column(name = "physical_ventilator_id", nullable = false)
    private UUID physicalVentilatorId;

    /** Auto-populated by Hibernate on INSERT; immutable thereafter. */
    @CreationTimestamp
    @Column(name = "evaluation_time", nullable = false, updatable = false)
    private OffsetDateTime evaluationTime;

    /** Respiratory rate in breaths/min. DB CHECK: 0–80. */
    @Column(name = "f")
    private BigDecimal f;

    /** Tidal volume in mL. DB CHECK: &gt; 0. */
    @Column(name = "vt")
    private BigDecimal vt;

    /** Arterial O₂ partial pressure in mmHg. DB CHECK: 0–700. */
    @Column(name = "pao2")
    private BigDecimal pao2;

    /** Fraction of inspired O₂ (0.21–1.00). DB CHECK: BETWEEN 0.21 AND 1.0. */
    @Column(name = "fio2")
    private BigDecimal fio2;

    /** Plateau airway pressure in cmH₂O. DB CHECK: &gt; peep. */
    @Column(name = "pplat")
    private BigDecimal pplat;

    /** Total positive end-expiratory pressure in cmH₂O. DB CHECK: &gt;= 0. */
    @Column(name = "peep")
    private BigDecimal peep;

    /**
     * Optional free-form ventilator parameters stored as JSONB.
     * Hibernate serialises the map to JSON automatically via {@code @JdbcTypeCode}.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extended_parameters", columnDefinition = "jsonb")
    private Map<String, Object> extendedParameters;

    /** Computed RSBI snapshot — {@code f / (Vt[L])} in breaths·min⁻¹·L⁻¹. Stored for audit. */
    @Column(name = "rsbi_snapshot")
    private BigDecimal rsbiSnapshot;

    /** Computed PaFi snapshot — {@code PaO₂ / FiO₂} in mmHg. Stored for audit. */
    @Column(name = "pafi_snapshot")
    private BigDecimal pafiSnapshot;

    /** Computed Cstat snapshot — {@code Vt / (Pplat − PEEP)} in mL/cmH₂O. Stored for audit. */
    @Column(name = "cstat_snapshot")
    private BigDecimal cstatSnapshot;

    /**
     * RSBI weaning-outcome interpretation captured at evaluation time.
     *
     * <p>Persisted (rather than re-derived from {@link #rsbiSnapshot}) so the
     * clinical judgement recorded for this evaluation remains stable even if the
     * classification thresholds in {@link RsbiInterpretation} are later revised.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "rsbi_interpretation", length = 20)
    private RsbiInterpretation rsbiInterpretation;

    /**
     * PaFi Berlin-Definition ARDS classification captured at evaluation time.
     * Persisted for the same threshold-drift reason as {@link #rsbiInterpretation}.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "pafi_classification", length = 20)
    private PafiClassification pafiClassification;

    /**
     * Static-compliance interpretation captured at evaluation time.
     * Persisted for the same threshold-drift reason as {@link #rsbiInterpretation}.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "cstat_interpretation", length = 20)
    private CstatInterpretation cstatInterpretation;

    /** {@code true} if any clinical threshold was breached during this evaluation. */
    @Column(name = "alert_triggered", nullable = false)
    @Builder.Default
    private boolean alertTriggered = false;

    /**
     * UUID of the authenticated therapist who submitted this evaluation.
     * Extracted from the JWT {@code sub} claim at the service layer.
     * Raw UUID FK to {@code users.id} — {@code UserJpaEntity} not yet modelled.
     */
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
}
