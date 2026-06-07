package wfederico.pneumacare.clinical.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Request payload for {@code POST /api/v1/evaluations}.
 *
 * <p>The caller supplies raw ventilator readings in the units described below.
 * The service layer computes the three clinical indices (RSBI, PaFi, Cstat)
 * and persists them alongside the raw inputs as an immutable audit record.
 *
 * <h2>Unit conventions</h2>
 * <ul>
 *   <li>{@code f}     — breaths per minute (0–80)</li>
 *   <li>{@code vt}    — tidal volume in <strong>mL</strong> (&gt; 0)</li>
 *   <li>{@code pao2}  — mmHg (0–700)</li>
 *   <li>{@code fio2}  — dimensionless fraction (0.21–1.00)</li>
 *   <li>{@code pplat} — cmH₂O, must be strictly &gt; {@code peep}</li>
 *   <li>{@code peep}  — cmH₂O (&gt;= 0)</li>
 * </ul>
 *
 * <h2>Cross-context references</h2>
 * {@code patientId}, {@code shiftId}, and {@code physicalVentilatorId} are UUIDs
 * that reference records in other bounded contexts. Their existence is enforced by
 * database FK constraints; no JPA-level validation is performed.
 */
@Schema(description = "Ventilator reading payload for evaluation persistence. " +
        "All clinical indices (RSBI, PaFi, Cstat) are computed server-side and " +
        "returned in the 201 response.")
public record CreateEvaluationRequest(

        @Schema(description = "UUID of the admitted patient (patients.id).",
                example = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11")
        @NotNull(message = "El ID del paciente es obligatorio")
        UUID patientId,

        @Schema(description = "UUID of the active medical shift.",
                example = "bbbbbbbb-0000-0000-0000-000000000001")
        @NotNull(message = "El ID del turno es obligatorio")
        UUID shiftId,

        @Schema(description = "UUID of the physical ventilator used for this reading.",
                example = "cccccccc-0000-0000-0000-000000000001")
        @NotNull(message = "El ID del ventilador es obligatorio")
        UUID physicalVentilatorId,

        @Schema(description = "Respiratory rate in breaths/min. Range: 0–80.",
                example = "15", minimum = "0", maximum = "80")
        @NotNull(message = "La frecuencia respiratoria es obligatoria")
        @DecimalMin(value = "0",  message = "La frecuencia respiratoria debe ser >= 0")
        @DecimalMax(value = "80", message = "La frecuencia respiratoria debe ser <= 80")
        BigDecimal f,

        @Schema(description = "Tidal volume in mL. Must be > 0.",
                example = "500", minimum = "0")
        @NotNull(message = "El volumen tidal es obligatorio")
        @DecimalMin(value = "0", inclusive = false,
                message = "El volumen tidal debe ser mayor que 0")
        BigDecimal vt,

        @Schema(description = "Arterial O\u2082 partial pressure in mmHg. Range: 0–700.",
                example = "85", minimum = "0", maximum = "700")
        @NotNull(message = "El PaO\u2082 es obligatorio")
        @DecimalMin(value = "0",   message = "El PaO\u2082 debe ser >= 0")
        @DecimalMax(value = "700", message = "El PaO\u2082 debe ser <= 700")
        BigDecimal pao2,

        @Schema(description = "Fraction of inspired O\u2082 (0.21–1.00).",
                example = "0.40", minimum = "0.21", maximum = "1.00")
        @NotNull(message = "El FiO\u2082 es obligatorio")
        @DecimalMin(value = "0.21", message = "El FiO\u2082 debe ser >= 0.21")
        @DecimalMax(value = "1.0",  message = "El FiO\u2082 debe ser <= 1.0")
        BigDecimal fio2,

        @Schema(description = "Plateau airway pressure in cmH\u2082O. Must be > 0 and > peep.",
                example = "25", minimum = "0")
        @NotNull(message = "La presi\u00f3n meseta es obligatoria")
        @DecimalMin(value = "0", inclusive = false,
                message = "La presi\u00f3n meseta debe ser mayor que 0")
        BigDecimal pplat,

        @Schema(description = "Total PEEP in cmH\u2082O. Must be >= 0 and < pplat.",
                example = "5", minimum = "0")
        @NotNull(message = "El PEEP es obligatorio")
        @DecimalMin(value = "0", message = "El PEEP debe ser >= 0")
        BigDecimal peep,

        @Schema(description = "Optional free-form ventilator parameters stored as JSONB.")
        Map<String, Object> extendedParameters) {
}
