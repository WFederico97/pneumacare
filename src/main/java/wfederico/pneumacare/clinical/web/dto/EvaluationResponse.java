package wfederico.pneumacare.clinical.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import wfederico.pneumacare.clinical.domain.CstatInterpretation;
import wfederico.pneumacare.clinical.domain.PafiClassification;
import wfederico.pneumacare.clinical.domain.RsbiInterpretation;
import wfederico.pneumacare.clinical.infrastructure.persistence.EvaluationJpaEntity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response payload for {@code POST /api/v1/evaluations} (201 Created).
 *
 * <p>Mirrors every persisted column plus the three interpretation enums that are
 * computed server-side but not stored in the database (they are deterministic
 * functions of the snapshot values and can always be re-derived).
 *
 * <p>The {@link #from} factory method takes the saved entity together with the
 * interpretation results so the service layer does not need to re-compute them
 * after the save.
 */
@Schema(description = "Persisted evaluation record with computed clinical indices.")
public record EvaluationResponse(

        @Schema(description = "Unique evaluation UUID.", example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
        UUID id,

        @Schema(description = "Patient UUID.", example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
        UUID patientId,

        @Schema(description = "Medical shift UUID.")
        UUID shiftId,

        @Schema(description = "Physical ventilator UUID.")
        UUID physicalVentilatorId,

        @Schema(description = "ISO-8601 timestamp when the evaluation was recorded.")
        OffsetDateTime evaluationTime,

        @Schema(description = "Respiratory rate (breaths/min).", example = "15")
        BigDecimal f,

        @Schema(description = "Tidal volume (mL).", example = "500")
        BigDecimal vt,

        @Schema(description = "Arterial O\u2082 partial pressure (mmHg).", example = "85")
        BigDecimal pao2,

        @Schema(description = "Fraction of inspired O\u2082 (0.21\u20131.00).", example = "0.40")
        BigDecimal fio2,

        @Schema(description = "Plateau airway pressure (cmH\u2082O).", example = "25")
        BigDecimal pplat,

        @Schema(description = "Total PEEP (cmH\u2082O).", example = "5")
        BigDecimal peep,

        @Schema(description = "Computed RSBI snapshot \u2014 f\u00a0/\u00a0(Vt[L]) breaths\u00b7min\u207b\u00b9\u00b7L\u207b\u00b9.",
                example = "30.00")
        BigDecimal rsbiSnapshot,

        @Schema(description = "RSBI weaning outcome interpretation.")
        RsbiInterpretation rsbiInterpretation,

        @Schema(description = "Computed PaFi snapshot \u2014 PaO\u2082\u00a0/\u00a0FiO\u2082 (mmHg).",
                example = "212.50")
        BigDecimal pafiSnapshot,

        @Schema(description = "PaFi Berlin Definition ARDS classification.")
        PafiClassification pafiClassification,

        @Schema(description = "Computed Cstat snapshot \u2014 Vt\u00a0/\u00a0(Pplat\u2212PEEP) mL/cmH\u2082O.",
                example = "25.00")
        BigDecimal cstatSnapshot,

        @Schema(description = "Static lung compliance interpretation.")
        CstatInterpretation cstatInterpretation,

        @Schema(description = "True if any clinical threshold was breached.")
        boolean alertTriggered,

        @Schema(description = "UUID of the therapist who submitted this evaluation.")
        UUID createdBy) {

    /**
     * Maps a saved {@link EvaluationJpaEntity} together with its interpretation
     * results to this response DTO.
     *
     * @param entity            the saved (and auto-ID-populated) evaluation entity
     * @param rsbiInterpretation weaning outcome classification derived from the RSBI snapshot
     * @param pafiClassification Berlin Definition ARDS classification derived from the PaFi snapshot
     * @param cstatInterpretation lung compliance interpretation derived from the Cstat snapshot
     * @return fully-populated response record
     */
    public static EvaluationResponse from(
            EvaluationJpaEntity entity,
            RsbiInterpretation rsbiInterpretation,
            PafiClassification pafiClassification,
            CstatInterpretation cstatInterpretation) {

        return new EvaluationResponse(
                entity.getId(),
                entity.getPatientId(),
                entity.getShiftId(),
                entity.getPhysicalVentilatorId(),
                entity.getEvaluationTime(),
                entity.getF(),
                entity.getVt(),
                entity.getPao2(),
                entity.getFio2(),
                entity.getPplat(),
                entity.getPeep(),
                entity.getRsbiSnapshot(),
                rsbiInterpretation,
                entity.getPafiSnapshot(),
                pafiClassification,
                entity.getCstatSnapshot(),
                cstatInterpretation,
                entity.isAlertTriggered(),
                entity.getCreatedBy());
    }
}
