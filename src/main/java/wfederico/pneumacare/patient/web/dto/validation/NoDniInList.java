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
 * Class-level constraint on {@link wfederico.pneumacare.patient.web.dto.CreatePatientRequest}
 * that rejects requests where any entry in {@code additionalIdentifiers} has an identifier
 * type whose name is {@code "DNI"}.
 *
 * <p>The DNI must be supplied exclusively via the top-level {@code dni} field.
 * Accepting it also inside the list would create ambiguity and potential duplicate
 * PII records.
 *
 * <p>The validator ({@link NoDniInListValidator}) performs a single cached DB lookup
 * at startup to resolve the DNI type id, keeping per-request cost at zero.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * @NoDniInList
 * public record CreatePatientRequest(...) { }
 * }</pre>
 */
@Documented
@Constraint(validatedBy = NoDniInListValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface NoDniInList {

    /** Violation message when a DNI entry is found in {@code additionalIdentifiers}. */
    String message() default ExceptionMessageConstants.DNI_NOT_ALLOWED_IN_ADDITIONAL;

    /** Bean Validation groups. */
    Class<?>[] groups() default {};

    /** Bean Validation payload. */
    Class<? extends Payload>[] payload() default {};
}
