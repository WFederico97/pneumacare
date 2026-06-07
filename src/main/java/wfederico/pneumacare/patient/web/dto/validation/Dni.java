package wfederico.pneumacare.patient.web.dto.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import wfederico.pneumacare.shared.constants.ExceptionMessageConstants;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that an Argentine DNI value consists of exactly 7 or 8 numeric digits
 * ({@code ^\\d{7,8}$}).
 *
 * <p>A {@code null} or blank value is considered valid by this constraint — the
 * caller must also annotate the field with {@code @NotBlank} to reject missing values.
 * This separation of concerns allows different error messages for "missing" vs.
 * "malformed" DNI entries.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * @NotBlank(message = ExceptionMessageConstants.DNI_REQUIRED)
 * @Dni
 * String dni;
 * }</pre>
 */
@Documented
@Constraint(validatedBy = DniValidator.class)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Dni {

    /** Violation message returned when the value does not match the DNI pattern. */
    String message() default ExceptionMessageConstants.DNI_INVALID_FORMAT;

    /** Bean Validation groups. */
    Class<?>[] groups() default {};

    /** Bean Validation payload. */
    Class<? extends Payload>[] payload() default {};
}
