package wfederico.pneumacare.clinical.web.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Input payload for the Rapid Shallow Breathing Index (RSBI) calculation.
 *
 * <p>RSBI = Respiratory Rate (f) / Tidal Volume (VT)
 */
public record RsbiRequest(

        /** Respiratory rate in breaths per minute. Typical range: 10–40. */
        @NotNull
        @Positive
        @DecimalMax("80.0")
        double respiratoryRate,

        /** Tidal volume in litres. Typical range: 0.3–0.8 L. */
        @NotNull
        @DecimalMin("0.05")
        @DecimalMax("3.0")
        double tidalVolume
) {}

