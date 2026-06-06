package wfederico.pneumacare.clinical.application.strategy;

import org.springframework.stereotype.Component;

import static wfederico.pneumacare.shared.constants.ExceptionMessageConstants.UNKNOWN_BRAND_ERROR;

/**
 * Routes a brand string to the matching {@link VentilatorStrategy} instance.
 *
 * <p>Brand resolution is case-insensitive — the input is upper-cased before
 * lookup. A {@code null} or unrecognised brand throws
 * {@link IllegalArgumentException} with a Spanish user-facing message
 * defined in
 * {@link wfederico.pneumacare.shared.constants.ExceptionMessageConstants#UNKNOWN_BRAND_ERROR}.
 *
 * <p><b>Adding a new brand:</b>
 * <ol>
 *   <li>Create a new {@code @Component} implementing {@link VentilatorStrategy}.</li>
 *   <li>Add a constructor parameter and field of that type to this class.</li>
 *   <li>Add a {@code case} arm to {@link #resolve(String)}.</li>
 * </ol>
 */
@Component
public class VentilatorFactory {
    private final TecmeStrategy tecmeStrategy;
    private final NeumoventStrategy neumoventStrategy;

    public VentilatorFactory(TecmeStrategy tecmeStrategy, NeumoventStrategy neumoventStrategy) {
        this.tecmeStrategy = tecmeStrategy;
        this.neumoventStrategy = neumoventStrategy;
    }

    /**
     * Resolves the strategy for the given ventilator brand.
     *
     * @param brand ventilator brand identifier (case-insensitive,
     *              e.g. {@code "TECME"}, {@code "NEUMOVENT"})
     * @return the strategy instance registered for {@code brand}
     * @throws IllegalArgumentException if {@code brand} is {@code null} or unrecognised
     */
    public VentilatorStrategy resolve(String brand) {
        if (brand == null) {
            throw new IllegalArgumentException(UNKNOWN_BRAND_ERROR);
        }
        return switch (brand.toUpperCase()) {
            case "TECME"     -> tecmeStrategy;
            case "NEUMOVENT" -> neumoventStrategy;
            default -> throw new IllegalArgumentException(UNKNOWN_BRAND_ERROR + brand);
        };
    }
}
