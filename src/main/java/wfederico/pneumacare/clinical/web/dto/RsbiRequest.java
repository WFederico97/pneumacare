package wfederico.pneumacare.clinical.web.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Input payload for the Rapid Shallow Breathing Index (RSBI) calculation.
 *
 * <p>RSBI = Respiratory Rate (f) / Tidal Volume (VT)
 *
 * <p><b>PII note:</b> {@code nationalId} is accepted for correlation purposes
 * but is intentionally <em>never</em> added to any span attribute or metric
 * tag. The {@link wfederico.pneumacare.shared.telemetry.PiiSanitizingSpanExporter}
 * acts as a safety net should a future developer accidentally expose it.
 */
public record RsbiRequest(

        /** Respiratory rate in breaths per minute. Typical range: 10–40. */
        @NotNull
        @Positive
        @DecimalMax("80.0")
        Double respiratoryRate,

        /** Tidal volume in litres. Typical range: 0.3–0.8 L. */
        @NotNull
        @DecimalMin("0.05")
        @DecimalMax("3.0")
        Double tidalVolume,

        /**
         * Optional patient national identity document — used only for
         * correlation in the response. <strong>Must not be propagated to
         * telemetry attributes.</strong>
         */
        String nationalId
) {}
