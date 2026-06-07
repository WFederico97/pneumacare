package wfederico.pneumacare.patient.web.dto.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import wfederico.pneumacare.shared.constants.ValidationConstants;

/**
 * Stateless validator for the {@link Dni} constraint.
 *
 * <p>Returns {@code true} for {@code null} or blank values — let {@code @NotBlank}
 * handle the "missing" case so each violation produces a distinct, actionable
 * error message in Spanish.
 *
 * <p>All other non-null values are matched against {@link ValidationConstants#DNI_PATTERN}.
 */
public class DniValidator implements ConstraintValidator<Dni, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true; // deferred to @NotBlank
        }
        return value.matches(ValidationConstants.DNI_PATTERN);
    }
}
