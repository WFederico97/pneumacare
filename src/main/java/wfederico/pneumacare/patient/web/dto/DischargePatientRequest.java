package wfederico.pneumacare.patient.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import wfederico.pneumacare.patient.domain.Disposition;

import java.time.OffsetDateTime;

/** Discharge (episode closure) request. */
public record DischargePatientRequest(

        @Schema(description = "Clinical disposition of the closure.", example = "WARD")
        @NotNull(message = "La disposición es obligatoria")
        Disposition disposition,

        @Schema(description = "Closure instant; defaults to now when omitted.",
                example = "2026-07-24T14:30:00Z", nullable = true)
        OffsetDateTime dischargeDate) {
}
