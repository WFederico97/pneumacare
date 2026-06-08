package wfederico.pneumacare.clinical.domain;

/**
 * Catalogue of supported physical ventilator brands.
 *
 * <p>Each constant maps 1:1 to a brand-specific
 * {@link wfederico.pneumacare.clinical.application.strategy.VentilatorStrategy}
 * implementation, resolved at runtime by
 * {@link wfederico.pneumacare.clinical.application.strategy.VentilatorFactory}.
 *
 * <p>This enum is the canonical discriminator carried by
 * {@link wfederico.pneumacare.clinical.web.dto.CreateEvaluationRequest} so that
 * the persistence service can route raw readings through the correct unit
 * conversions before delegating to {@link
 * wfederico.pneumacare.clinical.application.ClinicalMathEngine}.
 *
 * <p><b>Adding a new brand:</b>
 * <ol>
 *   <li>Add a new constant here.</li>
 *   <li>Implement a matching {@code VentilatorStrategy} bean.</li>
 *   <li>Wire it into {@code VentilatorFactory#resolve(VentilatorBrand)}.</li>
 * </ol>
 */
public enum VentilatorBrand {
    /** TECME — baseline brand; tidal volume reported in mL. */
    TECME,

    /** Neumovent — tidal volume reported in litres (L). */
    NEUMOVENT
}
