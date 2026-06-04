package wfederico.pneumacare.clinical.web.dto;

import jakarta.validation.constraints.*;

public record CstatRequest(
        @NotNull
        @DecimalMin("50.0")
        @DecimalMax("1000.0")
        double tidalVolume,
        @NotNull
        @Positive
        @DecimalMax("60.0")
        double plateauPressure,
        @NotNull
        @PositiveOrZero
        @DecimalMax("30.0")
        double peepTotal

) { }
