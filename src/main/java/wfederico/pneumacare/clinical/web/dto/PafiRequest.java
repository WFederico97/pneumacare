package wfederico.pneumacare.clinical.web.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Input payload for the PaO₂/FiO₂ ratio (PaFi / P/F ratio) calculation.
 *
 * <p>PaFi = PaO₂ (mmHg) / FiO₂ (fraction)
 *
 * <p>Used for ARDS severity classification per the Berlin criteria.
 */
public record PafiRequest(

        /** Arterial partial pressure of oxygen in mmHg. Typical range: 40–600. */
        @NotNull
        @Positive
        @DecimalMax("700.0")
        Double pao2,

        /**
         * Fraction of inspired oxygen (dimensionless, 0.21–1.0).
         * 0.21 = room air; 1.0 = 100 % O₂.
         */
        @NotNull
        @DecimalMin("0.21")
        @DecimalMax("1.0")
        Double fio2
) {}

