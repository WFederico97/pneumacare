package wfederico.pneumacare.clinical.web.dto;

import jakarta.validation.constraints.*;

public record CstatRequest(
        @NotNull
        @DecimalMin("50.0")
        @DecimalMax("1000.0")
        Double tidalVolume,
        @NotNull
        @Positive
        @DecimalMax("60.0")
        Double plateauPressure,
        @NotNull
        @PositiveOrZero
        @DecimalMax("30.0")
        Double peepTotal

) { }
